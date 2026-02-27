#!/usr/bin/env python3
"""
GitHub Copilot Enterprise Token Fetcher

For enterprise accounts, you might need to use your existing GitHub token
obtained through your company's SSO/OAuth flow.

This script helps you:
1. Use an existing GitHub token (from gh CLI or manual)
2. Exchange it for a Copilot token
"""

import http.client
import json
import subprocess
import sys
import os

# VSCode Copilot OAuth Client ID
CLIENT_ID = "Iv1.b507a08c87ecfe98"

def get_gh_token():
    """Try to get GitHub token from gh CLI"""
    try:
        result = subprocess.run(
            ["gh", "auth", "token"],
            capture_output=True,
            text=True,
            timeout=10
        )
        if result.returncode == 0:
            return result.stdout.strip()
    except Exception:
        pass
    return None

def get_copilot_token(github_token):
    """Exchange GitHub token for Copilot token"""
    conn = http.client.HTTPSConnection("api.github.com")
    
    headers = {
        "Authorization": f"token {github_token}",
        "Accept": "application/json",
        "Content-Type": "application/json",
        "User-Agent": "GitHubCopilotChat/0.26.7",
        "Editor-Version": "vscode/1.85.1"
    }
    
    conn.request("POST", "/copilot_internal/v2/token", None, headers)
    response = conn.getresponse()
    data = response.read().decode()
    conn.close()
    
    return json.loads(data) if data else {}

def main():
    print("=" * 60)
    print("GitHub Copilot Enterprise Token Fetcher")
    print("=" * 60)
    print()
    
    # Try to get token from environment or gh CLI
    github_token = None
    
    # Check environment variable
    github_token = os.environ.get("GITHUB_TOKEN")
    
    if not github_token:
        # Try gh CLI
        print("🔍 Checking for GitHub token from 'gh' CLI...")
        github_token = get_gh_token()
    
    if github_token:
        print(f"✅ Found GitHub token: {github_token[:10]}...")
    else:
        print("❌ No GitHub token found.")
        print()
        print("Please provide your GitHub token (with Copilot access):")
        print()
        print("Option 1: Set GITHUB_TOKEN environment variable")
        print("  export GITHUB_TOKEN=gho_xxx")
        print()
        print("Option 2: Use 'gh' CLI and login first")
        print("  gh auth login")
        print()
        print("Option 3: Paste your token below")
        github_token = input("Enter your GitHub token: ").strip()
        
        if not github_token:
            print("No token provided, exiting.")
            sys.exit(1)
    
    print()
    print("🔄 Exchanging for Copilot token...")
    
    copilot_data = get_copilot_token(github_token)
    
    if "token" in copilot_data:
        copilot_token = copilot_data['token']
        expires_at = copilot_data.get('expires_at', 0)
        
        import time
        print("✅ Copilot token received!")
        print()
        print("=" * 60)
        print("🎉 SUCCESS! Here are your tokens:")
        print("=" * 60)
        print()
        print("📋 GitHub Token:")
        print(f"   {github_token[:10]}...{github_token[-4:] if len(github_token) > 14 else github_token}")
        print()
        print("📋 Copilot Token:")
        print(f"   {copilot_token[:20]}...")
        print()
        print("⏰ Token expires at:", time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(expires_at)))
        print()
        
        # Save to file
        try:
            config_dir = os.path.expanduser("~/.config/copilot-proxy")
            os.makedirs(config_dir, exist_ok=True)
            
            with open(os.path.join(config_dir, "github-token"), "w") as f:
                f.write(github_token)
            
            with open(os.path.join(config_dir, "copilot-token.json"), "w") as f:
                json.dump({"token": copilot_token, "expiresAt": expires_at}, f)
            
            print(f"💾 Tokens saved to {config_dir}")
            print()
            print("You can now start the proxy server and it will use these tokens.")
        except Exception as e:
            print(f"⚠️  Could not save tokens: {e}")
        
    else:
        print(f"❌ Failed to get Copilot token: {copilot_data}")
        print()
        print("Possible reasons:")
        print("  - Token doesn't have Copilot access")
        print("  - Enterprise SSO not authorized")
        print("  - Token is expired or invalid")
        sys.exit(1)

if __name__ == "__main__":
    main()
