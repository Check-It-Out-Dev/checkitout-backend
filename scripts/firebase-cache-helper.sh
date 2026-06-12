#!/bin/bash
# Quick Firebase cache integration for existing Docker builds

# This script can be sourced in your GitHub Actions workflow
# to enable Firebase Storage as Docker cache backend

# Configuration
export FIREBASE_PROJECT="${FIREBASE_PROJECT:-checkitout-test}"
export FIREBASE_BUCKET="${FIREBASE_BUCKET:-checkitout-test.appspot.com}"
export DOCKER_CACHE_PATH="docker-cache"

# Function to setup Firebase cache
setup_firebase_docker_cache() {
    echo "🔥 Enabling Firebase Storage as Docker cache..."
    
    # Check if service account is provided
    if [ -z "$FIREBASE_SERVICE_ACCOUNT" ]; then
        echo "❌ FIREBASE_SERVICE_ACCOUNT not set"
        return 1
    fi
    
    # Install gcloud CLI if not present
    if ! command -v gcloud &> /dev/null; then
        echo "📦 Installing Google Cloud SDK..."
        echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | sudo tee -a /etc/apt/sources.list.d/google-cloud-sdk.list
        curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key --keyring /usr/share/keyrings/cloud.google.gpg add -
        sudo apt-get update -qq && sudo apt-get install -y -qq google-cloud-sdk
    fi
    
    # Authenticate
    echo "$FIREBASE_SERVICE_ACCOUNT" > /tmp/firebase-sa.json
    gcloud auth activate-service-account --key-file=/tmp/firebase-sa.json --quiet
    gcloud config set project $FIREBASE_PROJECT --quiet
    
    # Create bucket structure if needed
    gsutil -q mb gs://${FIREBASE_BUCKET}/${DOCKER_CACHE_PATH} 2>/dev/null || true
    
    # Set lifecycle for auto-cleanup (7 days)
    cat > /tmp/lifecycle.json << EOF
{
  "lifecycle": {
    "rule": [{
      "action": {"type": "Delete"},
      "condition": {"age": 7, "matchesPrefix": ["${DOCKER_CACHE_PATH}/"]}
    }]
  }
}
EOF
    gsutil -q lifecycle set /tmp/lifecycle.json gs://${FIREBASE_BUCKET} 2>/dev/null || true
    rm -f /tmp/lifecycle.json
    
    # Export cache configuration
    export BUILDX_CACHE_FIREBASE="gs://${FIREBASE_BUCKET}/${DOCKER_CACHE_PATH}"
    export DOCKER_BUILDKIT=1
    
    echo "✅ Firebase cache ready at: $BUILDX_CACHE_FIREBASE"
    
    # Cleanup function
    trap 'rm -f /tmp/firebase-sa.json' EXIT
    
    return 0
}

# Function to get cache arguments for docker buildx
get_firebase_cache_args() {
    local image_name="$1"
    local cache_key="${image_name//\//-}-$(date +%Y%m%d-%H)"
    
    # Return cache arguments
    echo "--cache-from type=gha"
    echo "--cache-from type=registry,ref=ghcr.io/${image_name}:buildcache"
    echo "--cache-to type=gha,mode=max"
    echo "--cache-to type=registry,ref=ghcr.io/${image_name}:buildcache,mode=max,compression=zstd"
    
    # Note: Direct GCS cache backend requires custom BuildKit build
    # For now, we use hybrid approach with registry + GHA
}

# Function to sync cache to/from Firebase
sync_docker_cache_to_firebase() {
    local image_name="$1"
    local cache_dir="/tmp/docker-cache-export"
    
    echo "💾 Syncing Docker cache to Firebase Storage..."
    
    # Export cache layers
    mkdir -p "$cache_dir"
    docker save "$image_name" | tar -C "$cache_dir" -xf -
    
    # Upload to Firebase with compression
    gsutil -m -o GSUtil:parallel_composite_upload_threshold=50M \
        rsync -r -d -J -C \
        "$cache_dir" "gs://${FIREBASE_BUCKET}/${DOCKER_CACHE_PATH}/${image_name//\//-}/"
    
    # Cleanup
    rm -rf "$cache_dir"
    
    echo "✅ Cache synced to Firebase"
}

# Function to restore cache from Firebase
restore_docker_cache_from_firebase() {
    local image_name="$1"
    local cache_dir="/tmp/docker-cache-import"
    
    echo "📥 Restoring Docker cache from Firebase Storage..."
    
    # Check if cache exists
    if ! gsutil -q stat "gs://${FIREBASE_BUCKET}/${DOCKER_CACHE_PATH}/${image_name//\//-}/manifest.json" 2>/dev/null; then
        echo "ℹ️ No Firebase cache found for $image_name"
        return 1
    fi
    
    # Download cache
    mkdir -p "$cache_dir"
    gsutil -m -o GSUtil:parallel_composite_upload_threshold=50M \
        rsync -r -J -C \
        "gs://${FIREBASE_BUCKET}/${DOCKER_CACHE_PATH}/${image_name//\//-}/" "$cache_dir"
    
    # Import to Docker
    tar -C "$cache_dir" -cf - . | docker load
    
    # Cleanup
    rm -rf "$cache_dir"
    
    echo "✅ Cache restored from Firebase"
    return 0
}

# Function to show cache usage
show_firebase_cache_usage() {
    echo "📊 Firebase Docker cache usage:"
    gsutil du -sh "gs://${FIREBASE_BUCKET}/${DOCKER_CACHE_PATH}" 2>/dev/null || echo "No cache found"
    
    echo ""
    echo "📁 Cached images:"
    gsutil ls "gs://${FIREBASE_BUCKET}/${DOCKER_CACHE_PATH}/" 2>/dev/null | head -20
}

# Function to clean old cache
clean_firebase_cache() {
    local days="${1:-7}"
    echo "🧹 Cleaning Firebase cache older than $days days..."
    
    # This is handled by lifecycle policy, but can force clean
    gsutil -m rm -r "gs://${FIREBASE_BUCKET}/${DOCKER_CACHE_PATH}/**" \
        -c "age(days) > $days" 2>/dev/null || true
    
    echo "✅ Cleanup complete"
}

# Export functions for use in other scripts
export -f setup_firebase_docker_cache
export -f get_firebase_cache_args
export -f sync_docker_cache_to_firebase
export -f restore_docker_cache_from_firebase
export -f show_firebase_cache_usage
export -f clean_firebase_cache

# If script is run directly, show usage
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    echo "Firebase Docker Cache Helper Functions"
    echo "====================================="
    echo ""
    echo "Source this script to use Firebase Storage as Docker cache:"
    echo "  source $0"
    echo ""
    echo "Available functions:"
    echo "  setup_firebase_docker_cache    - Initialize Firebase cache"
    echo "  get_firebase_cache_args IMAGE  - Get buildx cache arguments"
    echo "  sync_docker_cache_to_firebase  - Upload cache to Firebase"
    echo "  restore_docker_cache_from_firebase - Download cache from Firebase"
    echo "  show_firebase_cache_usage      - Show cache statistics"
    echo "  clean_firebase_cache [DAYS]    - Clean old cache"
    echo ""
    echo "Example usage in GitHub Actions:"
    echo '  - name: Setup Firebase Cache'
    echo '    env:'
    echo '      FIREBASE_SERVICE_ACCOUNT: ${{ secrets.FIREBASE_SERVICE_ACCOUNT_TEST }}'
    echo '    run: |'
    echo '      source scripts/firebase-cache-helper.sh'
    echo '      setup_firebase_docker_cache'
fi
