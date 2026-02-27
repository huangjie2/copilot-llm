#!/usr/bin/env python3
"""
GitHub Copilot Token Fetcher

This script helps you obtain a GitHub token that can be used with the Copilot API proxy.
It implements the OAuth device flow used by VSCode Copilot.

Usage:
    python3 get_copilot_token.py

The script will:
1. Start a device flow authentication
2. Display a URL and code for you to verify
3. Wait for you to complete the verification
4. Display the GitHub token and Copilot token
"""

import http.client
import json
import time
import sys

# VSCode Copilot OAuth Client ID
CLIENT_ID = "Iv1.b507a08c87ecfe98"
SCOPES = "read:user"

def make_request(host, path, data=None, headers=None, method="POST"):
    """Make HTTP request and return response"""
    if headers is None:
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json"
        }
    
    conn = http.client.HTTPSConnection(host)
    body = json.dumps(data) if data else None
    
    conn.request(method, path, body, headers)
    response = conn.getresponse()
    
    data = response.read().decode()
    conn.close()
    
    return json.loads(data) if data else {}

def start_device_flow():
    """Start the device flow authentication"""
    print("🔐 Starting GitHub Device Flow Authentication...")
    print()
    
    data = make_request(
        "github.com",
        "/login/device/code",
        {"client_id": CLIENT_ID, "scope": SCOPES}
    )
    
    if "error" in data:
        print(f"❌ Error: {data}")
        sys.exit(1)
    
    return data

def poll_for_token(device_code):
    """Poll for the access token"""
    data = make_request(
        "github.com",
        "/login/oauth/access_token",
        {
            "client_id": CLIENT_ID,
            "device_code": device_code,
            "grant_type": "urn:ietf:params:oauth:grant-type:device_code"
        }
    )
    
    return data

def get_copilot_token(github_token):
    """Exchange GitHub token for Copilot token"""
    data = make_request(
        "api.github.com",
        "/copilot_internal/v2/token",
        headers={
            "Authorization": f"token {github_token}",
            "Accept": "application/json",
            "Content-Type": "application/json",
            "User-Agent": "GitHubCopilotChat/0.26.7",
            "Editor-Version": "vscode/1.85.1"
        },
        method="POST"
    )
    
    return data

def main():
    print("=" * 60)
    print("GitHub Copilot Token Fetcher")
    print("=" * 60)
    print()
    
    # Step 1: Start device flow
    device_data = start_device_flow()
    
    print(f"📱 Please visit: {device_data['verification_uri']}")
    print(f"🔑 Enter this code: {device_data['user_code']}")
    print()
    print("⏳ Waiting for you to authorize...")
    print("   (The code expires in {} seconds)".format(device_data.get('expires_in', 900)))
    print()
    
    # Step 2: Poll for token
    device_code = device_data['device_code']
    interval = device_data.get('interval', 5)
    expires_in = device_data.get('expires_in', 900)
    start_time = time.time()
    
    github_token = None
    
    while time.time() - start_time < expires_in:
        time.sleep(interval)
        
        result = poll_for_token(device_code)
        
        if "access_token" in result:
            github_token = result['access_token']
            print("✅ GitHub token received!")
            break
        elif result.get("error") == "authorization_pending":
            print(".", end="", flush=True)
        elif result.get("error"):
            print()
            print(f"❌ Error: {result.get('error_description', result['error'])}")
            sys.exit(1)
    
    if not github_token:
        print()
        print("❌ Timeout waiting for authorization")
        sys.exit(1)
    
    print()
    
    # Step 3: Get Copilot token
    print("🔄 Fetching Copilot token...")
    copilot_data = get_copilot_token(github_token)
    
    if "token" in copilot_data:
        copilot_token = copilot_data['token']
        expires_at = copilot_data.get('expires_at', 0)
        
        print("✅ Copilot token received!")
        print()
        print("=" * 60)
        print("🎉 SUCCESS! Here are your tokens:")
        print("=" * 60)
        print()
        print("📋 GitHub Token (for /v1/auth/token endpoint):")
        print(f"   {github_token}")
        print()
        print("📋 Copilot Token (direct API access):")
        print(f"   {copilot_token}")
        print()
        print("⏰ Token expires at:", time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(expires_at)))
        print()
        print("=" * 60)
        print("📝 Usage with the proxy:")
        print("=" * 60)
        print()
        print("# Set the GitHub token:")
        print(f'curl -X POST http://localhost:8080/v1/auth/token \\')
        print(f'  -H "Content-Type: application/json" \\')
        print(f'  -d \'{{"token":"{github_token[:10]}..."}}\'')
        print()
        print("# Then use the chat API:")
        print('curl -X POST http://localhost:8080/v1/chat/completions \\')
        print('  -H "Content-Type: application/json" \\')
        print('  -d \'{"model":"gpt-4o","messages":[{"role":"user","content":"Hello"}]\'}')
        print()
        
        # Save tokens to file
        token_file = None
        try:
            import os
            config_dir = os.path.expanduser("~/.config/copilot-proxy")
            os.makedirs(config_dir, exist_ok=True)
            
            # Save GitHub token
            with open(os.path.join(config_dir, "github-token"), "w") as f:
                f.write(github_token)
            
            # Save Copilot token with expiry
            with open(os.path.join(config_dir, "copilot-token.json"), "w") as f:
                json.dump({"token": copilot_token, "expiresAt": expires_at}, f)
            
            token_file = os.path.join(config_dir, "github-token")
            print(f"💾 Tokens saved to {config_dir}")
        except Exception as e:
            print(f"⚠️  Could not save tokens to file: {e}")
        
    else:
        print(f"❌ Failed to get Copilot token: {copilot_data}")
        print()
        print("Your GitHub token (you may need to refresh manually):")
        print(f"   {github_token}")

if __name__ == "__main__":
    main()
