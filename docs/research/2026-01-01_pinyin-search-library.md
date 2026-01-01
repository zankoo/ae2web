# JavaScript 拼音搜索库调研报告

> 日期: 2026-01-01
> 目标: 为 AE2 Web Terminal 前端选择合适的拼音搜索库

## 核心发现

**推荐使用 `pinyin-match`** - 专为搜索匹配设计，轻量、API简洁、返回匹配位置便于高亮。

## 库对比

| 库 | 体积 | 专注搜索 | 返回位置 | GitHub Stars | 推荐度 |
|---|---|---|---|---|---|
| **pinyin-match** | ~50KB | ✅ | ✅ | 200+ | ⭐⭐⭐⭐⭐ |
| pinyin-engine | ~100KB | ✅ | ❌ | 300+ | ⭐⭐⭐⭐ |
| pinyin-pro | 927KB | ❌ | ❌ | 4118 | ⭐⭐⭐ |
| pinyin | 48.4MB | ❌ | ❌ | 7606 | ⭐⭐ |

## pinyin-match 详细分析

### 优势
1. **专为搜索设计** - 不是通用拼音转换库，专注匹配场景
2. **返回匹配位置** - 返回 `[start, end]` 便于实现搜索高亮
3. **轻量级** - 约 50KB，对前端加载影响小
4. **支持多音字** - 如 "曾" 同时匹配 zeng/ceng
5. **支持首字母** - "钻石剑" 可用 "zsj" 搜索
6. **实战验证** - rubick、vanblog、uTools 等项目使用

### API 示例

```javascript
import PinyinMatch from 'pinyin-match';

// 首字母匹配
PinyinMatch.match('钻石剑', 'zsj');       // [0, 2]

// 全拼匹配
PinyinMatch.match('钻石剑', 'zuanshi');   // [0, 1]

// 部分拼音匹配
PinyinMatch.match('钻石剑', 'zuanshij');  // [0, 2]

// 汉字匹配
PinyinMatch.match('钻石剑', '石剑');      // [1, 2]

// 无匹配
PinyinMatch.match('钻石剑', 'abc');       // false

// 忽略空格
PinyinMatch.match('钻石剑', 'zuan shi'); // [0, 1]

// 多音字
PinyinMatch.match('曾经沧海', 'zengjing'); // [0, 1]
PinyinMatch.match('曾经沧海', 'cengjing'); // [0, 1]
```

### 过滤列表用法

```javascript
// 过滤物品列表
const filteredItems = items.filter(item => {
    const name = item.name || item.id;
    return name.includes(searchTerm) || PinyinMatch.match(name, searchTerm);
});
```

## 实际使用案例 (GitHub)

### 1. VanBlog (博客系统)
```javascript
import pinyin from 'pinyin-match';

export const mutiSearch = (s, t) => {
  const source = s.toLowerCase();
  const target = t.toLowerCase();
  const rawInclude = source.includes(target);
  const pinYinInlcude = Boolean(pinyin.match(source, target));
  return rawInclude || pinYinInlcude;
};
```

### 2. Rubick (效率工具)
```javascript
function searchKeyValues(lists, value) {
  return lists.filter((item) => {
    return !!PinyinMatch.match(item, value);
  });
}
```

### 3. uTools QuickCommand
```javascript
// 高亮匹配文本
const highlightText = (text, filterText) => {
  const matchResult = pinyinMatch.match(text, filterText);
  if (!matchResult) return text;

  const [start, end] = matchResult;
  return text.slice(0, start) +
         '<mark>' + text.slice(start, end + 1) + '</mark>' +
         text.slice(end + 1);
};
```

## 其他库简评

### pinyin-engine
- 需要预先构建索引 `new PinyinEngine(data, ['name'])`
- 适合大数据集、对象数组搜索
- 不返回匹配位置，无法实现高亮

### pinyin-pro
- 功能最全面（声调、多音字、姓氏等）
- 准确率最高 99.846%
- 体积 927KB 过大，不适合轻量前端
- 用于"转换"而非"搜索"

### pinyin (hotoo)
- 经典老牌库
- 体积 48.4MB 极其臃肿
- 不推荐用于前端

## 集成方案

### 方案 A: CDN 引入 (推荐)
```html
<script src="https://cdn.jsdelivr.net/npm/pinyin-match@1.2.5/dist/main.min.js"></script>
```

### 方案 B: 本地打包
由于项目是 Minecraft mod，没有前端构建系统，推荐直接下载 minified 版本放入 resources/web/

## 结论

对于 AE2 Web Terminal 的物品搜索场景：

1. **选择 pinyin-match** - 最适合搜索匹配，轻量且 API 简洁
2. **使用 CDN 或本地引入** - 无需复杂构建系统
3. **结合原有搜索** - 先尝试原文匹配，再尝试拼音匹配
4. **可选高亮** - 利用返回的位置实现搜索高亮

---

## 参考链接

- [pinyin-match GitHub](https://github.com/xmflswood/pinyin-match)
- [pinyin-match npm](https://www.npmjs.com/package/pinyin-match)
- [pinyin-pro 官方文档](https://pinyin-pro.cn/)
