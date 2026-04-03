# 日本株ポートフォリオ看板

## 快速启动

### 1. 安装依赖（首次运行）
```bash
pip install yfinance flask flask-cors
```

### 2. 启动服务器
```bash
python3 server.py
```

### 3. 打开浏览器
访问 http://localhost:5555

---

## 功能说明

- **实时报价**：通过 yfinance 获取 Yahoo Finance 数据，60秒自动刷新
- **持仓管理**：点击每张股票卡片上的「✎ 编辑持仓/成本」按钮输入股数和成本价
- **盈亏计算**：自动计算市场价值、总盈亏金额、盈亏百分比
- **总览汇总**：顶部显示总市值、总成本、总盈亏、今日均涨跌
- **添加/删除股票**：底部输入代码添加（自动补 .T 后缀），卡片右上角 ✕ 删除
- **持仓数据持久化**：保存在 portfolio.json，重启服务器不会丢失

## 文件说明
- `server.py` — Flask 后端
- `index.html` — 前端界面
- `portfolio.json` — 持仓数据（自动生成）
