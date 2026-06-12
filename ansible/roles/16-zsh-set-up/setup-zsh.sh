No #!/bin/bash

# Complete Zsh + Powerlevel10k Setup Script for Ubuntu
# This script installs the same zsh environment you have on Mac

set -e  # Exit on error

echo "🚀 Starting Zsh + Powerlevel10k installation for Ubuntu..."

# Update system
echo "📦 Updating package lists..."
sudo apt update

# Install required packages
echo "📦 Installing required packages..."
sudo apt install -y \
    zsh \
    git \
    curl \
    wget \
    build-essential \
    fonts-powerline \
    fontconfig

# Install Oh My Zsh
echo "🐚 Installing Oh My Zsh..."
if [ ! -d "$HOME/.oh-my-zsh" ]; then
    sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)" "" --unattended
else
    echo "Oh My Zsh already installed, skipping..."
fi

# Clone Powerlevel10k theme
echo "🎨 Installing Powerlevel10k theme..."
git clone --depth=1 https://github.com/romkatv/powerlevel10k.git ${ZSH_CUSTOM:-$HOME/.oh-my-zsh/custom}/themes/powerlevel10k

# Install the 5 essential Zsh plugins
echo "🔌 Installing Zsh plugins..."

# 1. zsh-autosuggestions (Fish-like autosuggestions)
git clone https://github.com/zsh-users/zsh-autosuggestions ${ZSH_CUSTOM:-~/.oh-my-zsh/custom}/plugins/zsh-autosuggestions

# 2. zsh-syntax-highlighting (Real-time syntax highlighting)
git clone https://github.com/zsh-users/zsh-syntax-highlighting.git ${ZSH_CUSTOM:-~/.oh-my-zsh/custom}/plugins/zsh-syntax-highlighting

# 3. zsh-completions (Additional completion definitions)
git clone https://github.com/zsh-users/zsh-completions ${ZSH_CUSTOM:-~/.oh-my-zsh/custom}/plugins/zsh-completions

# 4. zsh-history-substring-search (Search history with arrow keys)
git clone https://github.com/zsh-users/zsh-history-substring-search ${ZSH_CUSTOM:-~/.oh-my-zsh/custom}/plugins/zsh-history-substring-search

# 5. fzf (Fuzzy finder - enhances Ctrl+R and more)
git clone --depth 1 https://github.com/junegunn/fzf.git ~/.fzf
~/.fzf/install --all

# Install recommended Nerd Fonts for Powerlevel10k
echo "🔤 Installing recommended fonts..."
mkdir -p ~/.local/share/fonts

# Download MesloLGS NF (recommended by Powerlevel10k)
cd ~/.local/share/fonts
wget -q https://github.com/romkatv/powerlevel10k-media/raw/master/MesloLGS%20NF%20Regular.ttf
wget -q https://github.com/romkatv/powerlevel10k-media/raw/master/MesloLGS%20NF%20Bold.ttf
wget -q https://github.com/romkatv/powerlevel10k-media/raw/master/MesloLGS%20NF%20Italic.ttf
wget -q https://github.com/romkatv/powerlevel10k-media/raw/master/MesloLGS%20NF%20Bold%20Italic.ttf

# Refresh font cache
fc-cache -fv

# Backup existing .zshrc
echo "📋 Backing up existing .zshrc..."
if [ -f ~/.zshrc ]; then
    cp ~/.zshrc ~/.zshrc.backup.$(date +%Y%m%d_%H%M%S)
fi

# Create new .zshrc with optimal configuration
echo "📝 Creating optimized .zshrc configuration..."
cat > ~/.zshrc << 'EOF'
# Enable Powerlevel10k instant prompt. Should stay close to the top of ~/.zshrc.
if [[ -r "${XDG_CACHE_HOME:-$HOME/.cache}/p10k-instant-prompt-${(%):-%n}.zsh" ]]; then
  source "${XDG_CACHE_HOME:-$HOME/.cache}/p10k-instant-prompt-${(%):-%n}.zsh"
fi

# Path to your oh-my-zsh installation.
export ZSH="$HOME/.oh-my-zsh"

# Set theme to Powerlevel10k
ZSH_THEME="powerlevel10k/powerlevel10k"

# Plugins configuration
plugins=(
    git
    zsh-autosuggestions
    zsh-syntax-highlighting
    zsh-completions
    zsh-history-substring-search
    fzf
    docker
    docker-compose
    sudo
    command-not-found
    colored-man-pages
    extract
)

# Oh My Zsh settings
DISABLE_AUTO_UPDATE="false"
ENABLE_CORRECTION="true"
COMPLETION_WAITING_DOTS="true"
HIST_STAMPS="yyyy-mm-dd"

# Load Oh My Zsh
source $ZSH/oh-my-zsh.sh

# User configuration

# History settings
HISTSIZE=50000
SAVEHIST=50000
setopt HIST_EXPIRE_DUPS_FIRST
setopt HIST_IGNORE_DUPS
setopt HIST_IGNORE_SPACE
setopt HIST_VERIFY
setopt SHARE_HISTORY

# Key bindings for history substring search
bindkey '^[[A' history-substring-search-up
bindkey '^[[B' history-substring-search-down
bindkey '^P' history-substring-search-up
bindkey '^N' history-substring-search-down

# Autosuggestions configuration
ZSH_AUTOSUGGEST_HIGHLIGHT_STYLE="fg=#666666"
ZSH_AUTOSUGGEST_STRATEGY=(history completion)
bindkey '^[[C' forward-char  # Right arrow accepts suggestion

# Aliases
alias ll='ls -alF'
alias la='ls -A'
alias l='ls -CF'
alias grep='grep --color=auto'
alias ..='cd ..'
alias ...='cd ../..'

# Load Powerlevel10k configuration
[[ ! -f ~/.p10k.zsh ]] || source ~/.p10k.zsh

# Load fzf
[ -f ~/.fzf.zsh ] && source ~/.fzf.zsh

# Custom functions
# Create directory and cd into it
mkcd() {
    mkdir -p "$1" && cd "$1"
}

# Extract various archive types
extract() {
    if [ -f $1 ]; then
        case $1 in
            *.tar.bz2)   tar xjf $1     ;;
            *.tar.gz)    tar xzf $1     ;;
            *.bz2)       bunzip2 $1     ;;
            *.rar)       unrar e $1     ;;
            *.gz)        gunzip $1      ;;
            *.tar)       tar xf $1      ;;
            *.tbz2)      tar xjf $1     ;;
            *.tgz)       tar xzf $1     ;;
            *.zip)       unzip $1       ;;
            *.Z)         uncompress $1  ;;
            *.7z)        7z x $1        ;;
            *)     echo "'$1' cannot be extracted via extract()" ;;
        esac
    else
        echo "'$1' is not a valid file"
    fi
}
EOF

# Install additional useful tools
echo "🛠️  Installing additional recommended tools..."
sudo apt install -y \
    eza \
    bat \
    ripgrep \
    fd-find \
    htop \
    ncdu \
    tldr

# Add tool aliases to .zshrc
cat >> ~/.zshrc << 'EOF'

# Modern tool aliases
alias ls='eza --icons --color=always --group-directories-first'
alias ll='eza -alF --icons --color=always --group-directories-first'
alias tree='eza --tree --icons --color=always'
alias cat='batcat --style=plain'
alias bat='batcat'
alias fd='fdfind'

# Git aliases
alias gs='git status'
alias ga='git add'
alias gc='git commit'
alias gp='git push'
alias gl='git log --oneline --graph --decorate'
EOF

# Change default shell to zsh
echo "🔄 Setting Zsh as default shell..."
chsh -s $(which zsh)

echo "✅ Installation complete!"
echo ""
echo "📌 Important next steps:"
echo "1. Log out and log back in (or run 'exec zsh') to start using Zsh"
echo "2. On first run, Powerlevel10k configuration wizard will start automatically"
echo "3. Choose the options that match your Mac setup"
echo "4. Make sure your terminal is using 'MesloLGS NF' font for proper icons"
echo ""
echo "🔤 Terminal font configuration:"
echo "   - For GNOME Terminal: Preferences → Profile → Text → Custom font → MesloLGS NF"
echo "   - For other terminals, look for font settings and select 'MesloLGS NF'"
echo ""
echo "💡 Your old .zshrc was backed up with timestamp"
echo ""
echo "🚀 Run 'exec zsh' to start using your new shell!"

