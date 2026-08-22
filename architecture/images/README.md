# Ảnh sơ đồ

Sinh tự động từ các khối ```mermaid``` trong file `.md` cùng thư mục cha. **Đừng sửa
tay** — sửa mã mermaid trong file `.md` rồi render lại.

## Vì sao là PNG chứ không phải SVG

mermaid-cli đặt chữ của nhãn vào trong `<foreignObject>`. Trình duyệt render được, nhưng
bộ lọc SVG của GitHub gỡ nó ra — sơ đồ hiện lên thành các ô rỗng không có chữ. Ảnh raster
có chữ nung sẵn nên hiển thị ở mọi nơi.

## Render lại

```bash
npx --yes @mermaid-js/mermaid-cli@11 \
  -i sodo.mmd -o sodo.png \
  -b white -c mermaid-config.json -w 1600 -s 2
```

`mermaid-config.json`:

```json
{
  "theme": "neutral",
  "flowchart": { "htmlLabels": false, "useMaxWidth": false },
  "themeVariables": { "fontFamily": "Segoe UI, Arial, sans-serif", "fontSize": "15px" }
}
```

`htmlLabels: false` là thứ khiến mermaid xuất `<text>` thật thay vì `<foreignObject>` —
cần cho cả trường hợp muốn quay lại dùng SVG.
