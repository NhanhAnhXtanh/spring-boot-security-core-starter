# CLAUDE.md

Claude Code đọc file này trước tiên trong repo.

**Toàn bộ rule cho dự án này tập trung trong [`AGENTS.md`](AGENTS.md) và thư mục [`rules/`](rules/).**

Để tránh duplicate, file này không lặp lại nội dung. Trước khi viết code đụng đến database, layer security, hay định nghĩa entity mới:

1. Đọc [`AGENTS.md`](AGENTS.md) — entry point & cheat sheet.
2. Đọc [`rules/data-access.md`](rules/data-access.md) — rule bắt buộc về `SecureDataManager`, `UnconstrainedDataManager`, và `AbstractAuditingEntity`.

Mọi AI assistant khác (Codex, Cursor, Copilot, Windsurf, …) cũng đọc cùng nguồn `AGENTS.md` + `rules/` → giữ hành vi nhất quán giữa các tool.
