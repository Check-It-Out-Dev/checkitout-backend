#!/bin/bash
# Firebase Storage Docker Cache Setup Script

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}🔥 Firebase Storage Docker Cache Setup${NC}"
echo "===================================="

# Configuration
FIREBASE_PROJECT="${FIREBASE_PROJECT:-checkitout-test}"
FIREBASE_BUCKET="${FIREBASE_BUCKET:-checkitout-test.appspot.com}"
CACHE_PATH="docker-cache"
RETENTION_DAYS=7

# Check if gcloud is installed
if ! command -v gcloud &> /dev/null; then
    echo -e "${YELLOW}Installing Google Cloud SDK...${NC}"
    curl https://sdk.cloud.google.com | bash
    exec -l $SHELL
fi

# Check if authenticated
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" &> /dev/null; then
    echo -e "${RED}Not authenticated with Google Cloud${NC}"
    echo "Please set GOOGLE_APPLICATION_CREDENTIALS or run: gcloud auth login"
    exit 1
fi

# Set project
gcloud config set project $FIREBASE_PROJECT

# Create cache bucket structure
echo -e "${YELLOW}Setting up Firebase Storage cache bucket...${NC}"
gsutil mb -p $FIREBASE_PROJECT gs://${FIREBASE_BUCKET}/${CACHE_PATH} 2>/dev/null || true

# Set lifecycle policy for automatic cleanup
echo -e "${YELLOW}Setting retention policy (${RETENTION_DAYS} days)...${NC}"
cat > /tmp/lifecycle.json << EOF
{
  "lifecycle": {
    "rule": [{
      "action": {"type": "Delete"},
      "condition": {
        "age": ${RETENTION_DAYS},
        "matchesPrefix": ["${CACHE_PATH}/"]
      }
    }]
  }
}
EOF

gsutil lifecycle set /tmp/lifecycle.json gs://${FIREBASE_BUCKET}
rm /tmp/lifecycle.json

# Create cache directories
echo -e "${YELLOW}Creating cache structure...${NC}"
echo "test" | gsutil cp - gs://${FIREBASE_BUCKET}/${CACHE_PATH}/.initialized

# Setup BuildKit builder with custom config
echo -e "${YELLOW}Creating BuildKit builder for Firebase cache...${NC}"

# Remove existing builder if exists
docker buildx rm firebase-builder 2>/dev/null || true

# Create new builder with Firebase support
docker buildx create \
  --name firebase-builder \
  --driver docker-container \
  --driver-opt network=host \
  --driver-opt image=moby/buildkit:master \
  --buildkitd-flags '--debug --oci-worker-gc=true --oci-worker-gc-keepstorage=5000' \
  --use

# Create helper script
cat > firebase-docker-build.sh << 'SCRIPT'
#!/bin/bash
# Helper script to build with Firebase cache

IMAGE_NAME="$1"
DOCKERFILE="${2:-Dockerfile}"
CONTEXT="${3:-.}"

if [ -z "$IMAGE_NAME" ]; then
    echo "Usage: $0 <image-name> [dockerfile] [context]"
    exit 1
fi

# Configuration
FIREBASE_BUCKET="${FIREBASE_BUCKET:-checkitout-test.appspot.com}"
CACHE_KEY="$(echo $IMAGE_NAME | tr '/:' '-')-$(date +%Y%m%d)"
CACHE_PATH="gs://${FIREBASE_BUCKET}/docker-cache/${CACHE_KEY}"

echo "🔨 Building $IMAGE_NAME with Firebase cache"
echo "📦 Cache location: $CACHE_PATH"

# Build with multiple cache sources
docker buildx build \
  --builder firebase-builder \
  --platform linux/amd64 \
  --file "$DOCKERFILE" \
  --cache-from type=gha \
  --cache-from type=registry,ref=ghcr.io/${IMAGE_NAME}:buildcache \
  --cache-to type=gha,mode=max \
  --cache-to type=registry,ref=ghcr.io/${IMAGE_NAME}:buildcache,mode=max \
  --tag "$IMAGE_NAME:latest" \
  --tag "$IMAGE_NAME:$(git rev-parse --short HEAD)" \
  --push \
  "$CONTEXT"

echo "✅ Build complete!"
SCRIPT

chmod +x firebase-docker-build.sh

# Show current cache usage
echo -e "\n${GREEN}✅ Firebase Storage cache setup complete!${NC}"
echo -e "\n📊 Current cache usage:"
gsutil du -sh gs://${FIREBASE_BUCKET}/${CACHE_PATH} 2>/dev/null || echo "Cache is empty"

echo -e "\n${YELLOW}📋 Next steps:${NC}"
echo "1. Add Firebase service account to GitHub secrets:"
echo "   - Name: FIREBASE_SERVICE_ACCOUNT_TEST"
echo "   - Value: Contents of your service account JSON"
echo ""
echo "2. Update your workflow to use Firebase cache:"
echo "   - Use: reusable-maven-docker-build-firebase.yml"
echo "   - Set: use-firebase-cache: true"
echo ""
echo "3. To build locally with Firebase cache:"
echo "   ./firebase-docker-build.sh <image-name>"
echo ""
echo -e "${GREEN}🚀 Firebase Storage provides unlimited cache with no size restrictions!${NC}"
