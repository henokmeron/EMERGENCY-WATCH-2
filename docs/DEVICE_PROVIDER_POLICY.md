# Device Provider Policy

- No provider-specific API may leak into the core event engine.
- Each provider exposes capabilities and normalizes supported signals.
- Missing measurements stay null.
- Provider claims must be sourced from manufacturer documentation or verified testing.
- A device being connected does not imply its measurements are clinically valid for every intended use.
- Galaxy Watch remains a development/test provider.
- Corsano integration must wait for official credentials/API contracts.
