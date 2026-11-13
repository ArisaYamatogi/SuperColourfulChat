# SuperColourfulChat 玩家权限说明

适用版本：SuperColourfulChat 1.0.0（Paper 系核心 1.16 – 26.3）
范围：本文档仅列出**玩家在游戏内使用本插件**所需的权限节点（Bukkit / 权限插件层面，如 LuckPerms），不包含操作系统、文件读写等软件访问权限。

## 一、本插件自有权限节点

插件仅有 **1 个** 权限节点：

| 权限节点 | 默认值 | 用途 | 被拒绝时的表现 |
|---|---|---|---|
| `scc.use` | `true`（全体玩家默认拥有，含非 OP） | 执行 `/scc` 开启 / 关闭"随机渐变聊天" | `/scc` 在到达插件前被 Bukkit 拦截，显示核心默认无权限提示（红色英文，可经 `bukkit.yml` 的 `settings.permission-message` 或权限插件覆盖），Tab 补全中隐藏 `/scc` |

说明：

- 强制点为 `plugin.yml` 中 `commands.scc.permission: scc.use`，由 Bukkit 命令机制检查；公屏 / 私聊监听器也会复查该节点，因此**撤权即时生效**（已开启的玩家被撤权后立即停止渐变）。
- `default: true` 意味着正常情况无需任何配置即可使用；只有权限插件显式负向（如 `scc.use false`）才会拒绝。
- 以下行为**不需要任何权限**，接收方也无需任何节点：接收他人渐变消息、低于 1.16 客户端的单色降级、EssentialsX 私聊（`/msg`、`/w` 等）渐变——私聊以玩家本身能使用私聊指令为前提。
- 开关状态存于内存，重登 / 重启后重置（行为特性，与权限无关）。

权限插件操作示例（LuckPerms）：

```
# 拒绝某玩家
/lp user <player> permission set scc.use false
# 恢复默认
/lp user <player> permission unset scc.use
# 整组拒绝
/lp group default permission set scc.use false
```

## 二、第三方权限节点（非本插件所有，仅影响联动行为）

| 节点 | 所属插件 | 默认 | 与本插件的关系 |
|---|---|---|---|
| `essentials.chat.spy.exempt` | EssentialsX | OP | 混合版本（发送 / 接收方分处 1.16 两侧）私聊场景下，**发送者或接收者任一方**持有该节点时，该条私聊不广播给 socialspy，与 Essentials 原生语义一致 |
| `essentials.msg`（及 `/w`、`/tell` 等别名对应权限） | EssentialsX | 依 Essentials 配置 | 玩家使用私聊指令的前提；本插件不检查它，只挂钩消息内容 |
| 手动颜色码翻译权限（chatcolor / Essentials 颜色权限等） | 对应插件 | 依各插件配置 | 决定 `&a`、`§l` 等手动码是否生效；本插件检测到消息含手动码时绕过渐变、原样显示 |

ViaVersion / ViaBackwards / ViaRewind 无玩家权限要求，客户端版本由插件自动检测。

## 三、权限行为测试矩阵（供真机验证）

| 场景 | 操作 | 预期 |
|---|---|---|
| 默认玩家开启 | 无权限插件，`/scc` | 提示"已开启"，公屏逐字渐变 |
| 默认玩家关闭 | 再 `/scc` | 提示"已关闭"，聊天恢复 |
| 负向拒绝 | `lp user P permission set scc.use false` 后 `/scc` | 默认无权限红字；Tab 无 `/scc` |
| 控制台 | 控制台执行 `scc` | "该命令只能由玩家使用。" |
| 无权限接收 | B 被负向，A 开启后发言 | B 仍见渐变（接收无需权限） |
| 低版本降级 | <1.16 客户端（Via）接收 A 消息 | 单色降级，无需权限 |
| 私聊渐变 | A 开启后 `/msg B 内容` | B 收渐变（低版本则降级），无需 scc 节点 |
| 重登 | A 开启 → 退出 → 重进 → 发言 | 无渐变，需重新 `/scc` |
| 重启 | 服务器重启后发言 | 全员关闭（内存态） |
| 动态授权 | 负向后 `set scc.use true`，立即 `/scc` | 即刻生效，无需重登 |
| 开启中撤权 | A 开启后被负向，再发言 | 渐变立即停止；`/scc` 被拒（功能已处于关闭） |
| spy 豁免 | 混合版本私聊任一方持 `essentials.chat.spy.exempt`（如 OP） | socialspy 收不到该条私聊 |