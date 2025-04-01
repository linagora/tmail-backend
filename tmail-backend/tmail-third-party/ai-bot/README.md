# AIBot third party

We provide a mailet extension for OpenAI-compatible APIs integration. The goal is to provide a mail bot at e.g. `gpt@example.com` that replies to users' questions.

To use this extension, please plug the external jar `tmail-ai-bot-jar-with-dependencies.jar` into your TMail application.

# AI Bot Configuration

## Overview

This document provides the setup instructions for configuring the AI Bot, specifically for the email response agent and suggestion agent. The configuration ensures a seamless setup by centralizing all settings in a single file.

---

## Configuration File

All AI Bot configuration detanils should be stored in the `ai.properties` file, located in the `conf` directory. Below is an example of the configuration format:

Sample `ai.properties` configuration:
```properties
apiKey=demo
botAddress=gpt@localhost
model=lucie
baseURL=https://chat.lucie.example.com
```
# AI Bot Configuration

## How Configuration Works

### Centralized Configuration

- The `ai.properties` file contains all key configuration items required for the AI Bot.
- These properties are then mapped to the POJO `AiBot` properties.

### Integration with AiBot

- The `ai.properties` file is used to configure the AI Bot in the `aibot` configuration class.
- This approach replaces the need to configure the AI Bot using the `mailetConfig`.
