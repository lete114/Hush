# Security

## Supported versions

This project is a personally maintained open-source tool and does not offer long-term support (LTS) versions. The latest commit on the repository is the current version.

## Reporting a vulnerability

Please report security issues privately via GitHub Security Advisory (or an issue):

<https://github.com/lete114/Hush/security/advisories>

Please do not publicly disclose details of unfixed vulnerabilities.

## Scope

Hush is a fully offline local tool (no network permission, no accounts, no data collection), so its attack surface is minimal. The main concerns are:

- The device admin / lock logic's attack surface (this project declares only the single `force-lock` policy)
- How honestly notifications and permissions describe themselves (permission purposes are not overstated)

Fixes are handled on a best-effort basis.