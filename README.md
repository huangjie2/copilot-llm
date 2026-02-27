# GitHub Copilot API Proxy

> ⚠️ **IMPORTANT: This project is for PERSONAL USE ONLY**

A lightweight OpenAI-compatible API proxy for GitHub Copilot, built with Quarkus.

**中文说明见下方**

---

## ⚠️ Disclaimer / 免责声明

**ENGLISH:**
- This project is a **personal API gateway** for self-use only
- **NOT intended for reverse engineering** GitHub Copilot's API
- **NOT for resale** or commercial purposes
- **NOT for sharing accounts** - each user must use their own GitHub Copilot subscription
- Use at your own risk. The author is not responsible for any consequences resulting from the use of this software
- This project is not affiliated with, endorsed by, or connected to GitHub or Microsoft
- Users must comply with [GitHub's Terms of Service](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service) and [GitHub Copilot Terms](https://docs.github.com/en/site-policy/github-terms/github-copilot-pre-release-additional-terms)

**中文:**
- 本项目仅为**个人自用 API 网关**
- **禁止用于逆向工程** GitHub Copilot 的 API
- **禁止转售**或用于商业目的
- **禁止共享账号** - 每个用户必须使用自己的 GitHub Copilot 订阅
- 使用风险自负。作者不对使用本软件造成的任何后果负责
- 本项目与 GitHub 或 Microsoft 无任何关联
- 用户必须遵守 [GitHub 服务条款](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service) 和 [GitHub Copilot 条款](https://docs.github.com/en/site-policy/github-terms/github-copilot-pre-release-additional-terms)

---

## Features

- ✅ OpenAI-compatible REST API
- ✅ Chat Completions (对话)
- ✅ Embeddings (向量嵌入)
- ✅ Streaming responses (流式返回)
- ✅ OAuth Device Flow authentication
- ✅ Support for Individual / Business / Enterprise accounts
- ✅ Token auto-refresh

## Supported Models

| Model | Provider |
|-------|----------|
| gpt-4o, gpt-4o-mini | OpenAI |
| gpt-4-turbo, gpt-4 | OpenAI |
| gpt-3.5-turbo | OpenAI |
| claude-3.5-sonnet, claude-3-opus | Anthropic |
| o1, o1-mini, o1-preview | OpenAI |

---

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- GitHub Copilot subscription (Individual, Business, or Enterprise)

### Build & Run

```bash
# Build
mvn clean compile

# Run in dev mode
mvn quarkus:dev

# Run in production mode
mvn clean package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

The server will start at `http://localhost:8080`

---

## Authentication

### Method 1: Device Flow (Recommended)

```bash
# Run the token fetcher
python3 get_copilot_token.py

# Follow the instructions to complete OAuth
```

Or use the API:

```bash
# Start device flow
curl -X POST http://localhost:8080/v1/auth/device

# Response:
# {
#   "device_code": "...",
#   "user_code": "XXXX-XXXX",
#   "verification_uri": "https://github.com/login/device",
#   "expires_in": 900,
#   "interval": 5
# }

# Poll for completion
curl -X POST http://localhost:8080/v1/auth/poll \
  -H "Content-Type: application/json" \
  -d '{"deviceCode":"YOUR_DEVICE_CODE"}'
```

### Method 2: Use Existing GitHub Token

If you already have a GitHub token (e.g., from `gh auth login`):

```bash
python3 get_enterprise_token.py

# Or set directly via API
curl -X POST http://localhost:8080/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"token":"gho_xxx"}'
```

---

## API Usage

### Chat Completions

```bash
# Non-streaming
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Hello, how are you?"}],
    "max_tokens": 100
  }'

# Streaming
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": true
  }'
```

### Embeddings

```bash
curl -X POST http://localhost:8080/v1/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding-ada-002",
    "input": "Hello world"
  }'
```

### List Models

```bash
curl http://localhost:8080/v1/models
```

---

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
copilot:
  # Account type: individual, business, enterprise
  account-type: individual
  
  # Default model
  default-model: gpt-4o
  
  # Token storage directory
  # token-dir: ~/.config/copilot-proxy
```

### For Enterprise Accounts

```yaml
copilot:
  account-type: enterprise
```

The API URL will automatically switch to `https://api.enterprise.githubcopilot.com`

---

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/v1/chat/completions` | POST | Chat completions (OpenAI compatible) |
| `/v1/embeddings` | POST | Create embeddings |
| `/v1/models` | GET | List available models |
| `/v1/models/{id}` | GET | Get model info |
| `/v1/auth/device` | POST | Start OAuth device flow |
| `/v1/auth/poll` | POST | Poll for auth completion |
| `/v1/auth/token` | POST | Set GitHub token directly |
| `/v1/auth/status` | GET | Check authentication status |
| `/v1/auth/logout` | POST | Clear stored tokens |

---

## Use with OpenAI SDK

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8080/v1",
    api_key="not-needed"  # Copilot token is managed by the proxy
)

response = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "Hello!"}]
)
print(response.choices[0].message.content)
```

---

## Security Notes

1. **Never share your GitHub token or Copilot token**
2. Tokens are stored locally in `~/.config/copilot-proxy/`
3. Tokens are automatically refreshed before expiration
4. Use HTTPS in production environments

---

## License

This project is provided as-is for personal use only. No license is granted for commercial use, redistribution, or any purpose that violates GitHub's Terms of Service.

---

## Acknowledgments

- Built with [Quarkus](https://quarkus.io/)
- Inspired by similar community projects

---

**使用本软件即表示您同意遵守上述条款和免责声明。**
