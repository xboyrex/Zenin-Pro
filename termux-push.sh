#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# ⚡ Zen'in Pro — Termux Automated GitHub Repo Creator, Pusher & APK CI Trigger
# Architected for: ㅤᏞᴀᴢʏㅤᎷꫝʏᴀɴк まやんく (@RoranoaxZORO | https://t.me/RoranoaxZORO)
# ==============================================================================

set -e

CYAN='\033[0;36m'
GREEN='\033[0;32m'
PURPLE='\033[0;35m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${PURPLE}==================================================================${NC}"
echo -e "${CYAN}   ⚡ Zen'in Pro — Termux GitHub Push & Auto-APK Release Script   ${NC}"
echo -e "${PURPLE}   Owner: ㅤᏞᴀᴢʏㅤᎷꫝʏᴀɴк まやんく (@RoranoaxZORO | t.me/RoranoaxZORO) ${NC}"
echo -e "${PURPLE}==================================================================${NC}"

# 1. Install required packages in Termux if running inside Termux
if command -v pkg >/dev/null 2>&1; then
    echo -e "${YELLOW}[1/6] Checking Termux dependencies (git, curl, jq, unzip, gh)...${NC}"
    pkg update -y
    pkg install -y git curl jq unzip gh
fi

# 2. Extract Zip if run next to Zenin-Pro-Browser-Full-Source.zip
if [ ! -f "settings.gradle.kts" ]; then
    if [ -f "Zenin-Pro-Browser-Full-Source.zip" ]; then
        echo -e "${CYAN}[*] Extracting Zenin-Pro-Browser-Full-Source.zip...${NC}"
        unzip -q -o Zenin-Pro-Browser-Full-Source.zip -d ./Zenin-Pro-Extracted
        cd ./Zenin-Pro-Extracted/Zen-in-Pro || cd ./Zenin-Pro-Extracted
    elif [ -d "Zen-in-Pro" ]; then
        cd Zen-in-Pro
    fi
fi

# 3. Collect GitHub Credentials & Repo Info
echo ""
read -p "Enter your GitHub Username [default: RoranoaxZORO]: " GH_USER
GH_USER=${GH_USER:-RoranoaxZORO}

read -p "Enter new GitHub Repository Name [default: Zenin-Pro]: " REPO_NAME
REPO_NAME=${REPO_NAME:-Zenin-Pro}

read -p "Repository Visibility (public/private) [default: public]: " REPO_VIS
REPO_VIS=${REPO_VIS:-public}

echo ""
echo -e "${YELLOW}Enter your GitHub Personal Access Token (classic 'repo' + 'workflow' scope):${NC}"
read -s -p "GitHub Token (input hidden): " GH_TOKEN
echo ""

if [ -z "$GH_TOKEN" ]; then
    echo -e "${RED}[!] Error: GitHub Token is required to create the repo and push workflows.${NC}"
    exit 1
fi

PRIVATE_BOOL="false"
if [ "$REPO_VIS" = "private" ]; then
    PRIVATE_BOOL="true"
fi

# 4. Create Repository via GitHub REST API
echo -e "${CYAN}[2/6] Creating GitHub repository ${GH_USER}/${REPO_NAME}...${NC}"
CREATE_RESP=$(curl -s -X POST \
  -H "Authorization: token ${GH_TOKEN}" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"${REPO_NAME}\",\"description\":\"Zen'in Pro — Material 3 Privacy Browser with Native AdGuard Pro++/Ultimate Engine, NextDNS DoH/DoT & 16-Thread Download Manager\",\"private\":${PRIVATE_BOOL},\"has_issues\":true,\"has_projects\":true,\"has_wiki\":true}")

if echo "$CREATE_RESP" | grep -q '"full_name"'; then
    echo -e "${GREEN}[✓] Repository https://github.com/${GH_USER}/${REPO_NAME} created!${NC}"
elif echo "$CREATE_RESP" | grep -q 'name already exists'; then
    echo -e "${YELLOW}[i] Repository ${GH_USER}/${REPO_NAME} already exists. Pushing to existing repository...${NC}"
else
    echo -e "${YELLOW}[i] GitHub API note: $(echo "$CREATE_RESP" | jq -r '.message // "Proceeding"')${NC}"
fi

# 5. Initialize Git & Commit Complete Source
echo -e "${CYAN}[3/6] Initializing Git repository & configuring owner identity...${NC}"
rm -rf .git
git init -b main
git config user.name "Lazy Mayank (@RoranoaxZORO)"
git config user.email "${GH_USER}@users.noreply.github.com"

chmod +x gradlew scripts/*.sh 2>/dev/null || true

echo -e "${CYAN}[4/6] Staging all Zen'in Pro source files, AdGuard filters & CI workflows...${NC}"
git add -A
git commit -m "feat: Initial release of Zen'in Pro v2.5 (AdGuard Pro++/Ultimate + NextDNS + Download Engine)"

# 6. Push Main Branch & Release Tag v2.5.0 to Trigger GitHub Actions APK Build
REMOTE_URL="https://${GH_USER}:${GH_TOKEN}@github.com/${GH_USER}/${REPO_NAME}.git"
git remote add origin "$REMOTE_URL"

echo -e "${CYAN}[5/6] Pushing 'main' branch to GitHub...${NC}"
git push -u origin main --force

echo -e "${CYAN}[6/6] Creating & pushing release tag 'v2.5.0' to trigger Auto-Release APK build...${NC}"
git tag -a v2.5.0 -m "Zen'in Pro v2.5.0 Release"
git push origin v2.5.0 --force

# Sanitize remote URL so token is not left in plain text in .git/config
git remote set-url origin "https://github.com/${GH_USER}/${REPO_NAME}.git"

echo ""
echo -e "${GREEN}==================================================================${NC}"
echo -e "${GREEN}🎉 SUCCESS! Zen'in Pro has been pushed to GitHub!${NC}"
echo -e "${CYAN}🔗 Repository:     https://github.com/${GH_USER}/${REPO_NAME}${NC}"
echo -e "${CYAN}⚙️  GitHub Actions: https://github.com/${GH_USER}/${REPO_NAME}/actions${NC}"
echo -e "${CYAN}📦 Auto Releases:  https://github.com/${GH_USER}/${REPO_NAME}/releases${NC}"
echo -e "${GREEN}==================================================================${NC}"
