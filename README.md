# GOL Check for JetBrains

> **This is a thin wrapper.** The one location for Check (install, hook mode, MCP, CLI, and the HTTP contract) is [check.golproductions.com](https://check.golproductions.com) · [golproductions/check](https://github.com/golproductions/check). Integrate from there.

The universal anti-hallucination engine for JetBrains IDEs.

AI agents hallucinate. Check catches it before it reaches your project. No AI inside. Deterministic. Sub-100ms.

## Install

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32408-gol-check---anti-hallucination-engine), or search "GOL Check" in your IDE's plugin settings.

That's it. Check activates a free key on first run. No signup, no API key to paste, no browser.

## Usage

- **Tools > Check > Validate Command with Check**: validate a command before running it
- **Right-click > Validate with Check**: validate selected text as a command
- Settings: **Settings > Tools > GOL Check**

## Pricing

120 free checks every day. After that, $0.0068 AUD per check. No subscription. Credits never expire.

| Tier | Checks |
|------|--------|
| Free | 120 / day |
| $5 | ~735 |
| $10 | ~1,470 |
| $25 | ~3,676 |
| $50 | ~7,352 |

## Bring your own key (optional)

A key is minted for you automatically. If you already have a Client ID, or want the same one across machines, set it in **Settings > Tools > GOL Check**, or:

```
export GOL_CLIENT_ID=your_key
```

## Works everywhere

Claude Code, Cursor, Gemini CLI, Windsurf, Devin Desktop, Continue, Amazon Q, Roo Code, Claude Desktop, JetBrains, VS Code, Neovim, Emacs, Sublime Text, Zed, and any MCP client.

Install all integrations at once:

```
npx @golproductions/check --install
```

## Manage

```
npx @golproductions/check --status      Show your client ID and where Check is installed
npx @golproductions/check --uninstall   Remove Check from every tool
```

## Links

- [Product page](https://www.golproductions.com/check.html)
- [Pricing](https://www.golproductions.com/pricing.html)
- [Updates](https://www.golproductions.com/updates.html)
- [Blog](https://www.golproductions.com/blog/)

## License

Copyright (c) 2026 GOL Productions. All rights reserved.
