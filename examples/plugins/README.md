# Example plugins

exteraGram-compatible plugins for YiliGram. Install one from **Settings → Plugins → Install from file**.

| File | Shows |
|---|---|
| `hello_command.plugin` | `on_send_message_hook` + `HookStrategy.MODIFY` (the exteraGram docs example) |
| `ghost_typing.plugin` | `add_hook` + `pre_request_hook` + `HookStrategy.CANCEL`, settings with `Switch` |
| `markdown_send.plugin` | `extera_utils.text_formatting.parse_text` → message entities |
| `yili_colors.plugin` | YiliGram's optional `yili_ui` module (M3 colors), `Text` settings with `on_click` |
| `ping_bulletin.plugin` | `ui.bulletin.BulletinHelper`; a quick self-test that sends nothing |
| `hook_titles.plugin` | Java method hooks: `MethodHook` on `ActionBar.setTitle` rewrites an argument |
