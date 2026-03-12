---
name: explain
description: "Explain code with analogy, ASCII diagram, and step-by-step walkthrough. Use when user asks 'how does X work?' or 'explain this code'."
user-invocable: true
context: fork
agent: Explore
argument-hint: "[file or class name]"
---

Отвечай на русском языке.

Explain the code or concept: $ARGUMENTS

Follow this structure:

1. **One-liner**: What does it do in plain Russian in 1 sentence?

2. **Аналогия**: Compare to something from everyday life (warehouse, conveyor, restaurant kitchen...)

3. **ASCII-диаграмма**: Show the flow/structure visually:
   ```
   [Component A] → [Component B] → [Result]
        ↑                  ↓
   [Trigger]          [Side Effect]
   ```

4. **Пошагово**: Walk through what happens step-by-step, reference specific file:line numbers

5. **Ловушка**: What is the most common mistake or non-obvious behavior here?

Focus on WHY, not just WHAT. Reference specific files and line numbers.
Keep it conversational and practical.
