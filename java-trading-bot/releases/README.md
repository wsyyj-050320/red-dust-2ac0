# releases 目录说明

仓库不再提交二进制压缩包，以避免在创建拉取请求或审核时因“禁止二进制文件”限制而失败。

如需发布离线分发包，请运行脚本生成 zip 文件，并在本地或外部分发：

```bash
cd java-trading-bot
./scripts/create-archive.sh
```

脚本会在 `dist/` 目录生成形如 `ai-trading-bot-YYYYMMDDHHMMSS.zip` 的文件。若要将其上传到 Release，请在生成后手动附加至 Release 资产，而不要直接提交到仓库。
