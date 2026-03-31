# CONTRIBUTING

## 기본 규칙

- `main`에 직접 작업하지 않는다.
- feature 브랜치에서 작업한다.
- PR merge 후 `main`을 최신화하고 feature 브랜치를 삭제한다.

## 권장 흐름

```bash
git switch main
git pull --ff-only
git switch -c feature/<task-name>

# work...
git add .
git commit -m "..."
git push -u origin feature/<task-name>
```
