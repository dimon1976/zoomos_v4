---
name: frontend-design
description: Create distinctive, production-grade frontend interfaces with high design quality. Use this skill when the user asks to build web components, pages, artifacts, or applications. Supports two stacks: (1) Thymeleaf + Bootstrap 5 for existing pages, (2) React + Vite for new standalone pages/widgets. Generates creative, polished code that avoids generic AI aesthetics.
---

This skill guides creation of distinctive, production-grade frontend interfaces for Zoomos v4. Avoids generic "AI slop" aesthetics. Implements real working code with exceptional attention to aesthetic details.

## Stack Context (Zoomos v4)

**Текущий стек (существующие страницы):**
- Thymeleaf + Bootstrap 5 + Vanilla JS
- Шаблоны в `src/main/resources/templates/`
- Фрагменты: `fragments/header.html`, `fragments/footer.html`, `fragments/sidebar.html`
- STOMP WebSocket для уведомлений

**Новый стек (новые страницы/виджеты):**
- React 18 + Vite
- Встраивается в Thymeleaf через `<div id="root">` + подключение собранного JS
- Spring Boot отдаёт REST API (`/api/**`)
- Стили: CSS Modules или Tailwind (на выбор)

**Когда использовать Thymeleaf:**
- Правки существующих страниц
- Небольшие улучшения UI
- Страницы с server-side логикой (форм, redirect, flash-attributes)

**Когда использовать React:**
- Новые страницы с богатой интерактивностью
- Дашборды с реалтайм-данными (Zoomos Check, статистика)
- Компоненты с complex state management

## Design Thinking

Before coding, understand the context and commit to a BOLD aesthetic direction:
- **Purpose**: What problem does this interface solve? Who uses it?
- **Tone**: Pick an extreme: brutally minimal, maximalist chaos, retro-futuristic, organic/natural, luxury/refined, playful/toy-like, editorial/magazine, brutalist/raw, art deco/geometric, soft/pastel, industrial/utilitarian.
- **Constraints**: Stack choice (Thymeleaf vs React), Bootstrap compatibility, existing sidebar/header layout.
- **Differentiation**: What makes this UNFORGETTABLE?

**CRITICAL**: Choose a clear conceptual direction and execute it with precision. Bold maximalism and refined minimalism both work — the key is intentionality, not intensity.

## Frontend Aesthetics Guidelines

Focus on:
- **Typography**: Choose fonts that are beautiful, unique, and interesting. Avoid generic fonts like Arial, Inter, Roboto. Pair a distinctive display font with a refined body font. For Thymeleaf pages — Google Fonts via CDN. For React — npm packages.
- **Color & Theme**: Commit to a cohesive aesthetic. Use CSS variables for consistency. Dominant colors with sharp accents. Zoomos v4 uses dark sidebar (`#1e293b`) — учитывай при выборе цветовой схемы.
- **Motion**: CSS animations for Thymeleaf, Framer Motion / CSS для React. One well-orchestrated page load with staggered reveals creates more delight than scattered micro-interactions.
- **Spatial Composition**: Unexpected layouts. Asymmetry. Overlap. Grid-breaking elements. Generous negative space OR controlled density.
- **Backgrounds & Visual Details**: Gradient meshes, noise textures, geometric patterns, layered transparencies, dramatic shadows.

NEVER use: overused font families (Inter, Roboto, Arial, system fonts), purple gradients on white backgrounds, predictable cookie-cutter layouts.

## Implementation Patterns

### Thymeleaf Page Template
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{fragments/layout}">
<head>
    <title>Page Title</title>
    <style>/* Page-specific styles here */</style>
</head>
<body>
<div layout:fragment="content">
    <!-- content -->
</div>
<th:block layout:fragment="scripts">
    <script>/* Page-specific JS */</script>
</th:block>
</body>
</html>
```

### React Widget встроенный в Thymeleaf
```html
<!-- В Thymeleaf шаблоне -->
<div id="react-dashboard"></div>
<script type="module" th:src="@{/js/dashboard.js}"></script>
```

```jsx
// src/main/resources/static/react/Dashboard.jsx
import { createRoot } from 'react-dom/client';
const root = createRoot(document.getElementById('react-dashboard'));
root.render(<Dashboard />);
```

### REST API Pattern (Spring Boot side)
```java
@RestController
@RequestMapping("/api/zoomos-check")
public class ZoomosCheckApiController {
    // JSON endpoints for React consumption
}
```

## Output Format

Always deliver:
1. **Stack choice** + reasoning (1 sentence)
2. **Aesthetic direction** (1 sentence — be specific)
3. **Working code** — complete, ready to use
4. **File paths** where to place the code in Zoomos v4 structure

Remember: Claude is capable of extraordinary creative work. Don't hold back — show what can truly be created when thinking outside the box and committing fully to a distinctive vision.
