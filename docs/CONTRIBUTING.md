# CONTRIBUTING

## 기본 규칙

- `main`에 직접 작업하지 않는다.
- feature 브랜치에서 작업한다.
- PR merge 후 `main`을 최신화하고 feature 브랜치를 삭제한다.
- 작업 시작 전에 이 저장소의 로컬 Git author 설정을 확인한다.

## 로컬 author 설정

개인 프로젝트에서는 전역 회사 계정 설정이 섞이지 않도록 저장소별 author를 고정하는 편이 안전하다.

```bash
git config user.name "soriqoo"
git config user.email "107284670+soriqoo@users.noreply.github.com"
git config --get user.name
git config --get user.email
```

권장 이유:
- 회사 계정 메일이 개인 저장소 커밋 이력에 섞이는 것을 방지한다.
- 여러 프로젝트를 동시에 운영할 때 author 정보가 저장소별로 분리된다.
- PR/히스토리 정리 비용을 초반에 줄일 수 있다.

## 권장 흐름

```bash
git config user.name "soriqoo"
git config user.email "107284670+soriqoo@users.noreply.github.com"

git switch main
git pull --ff-only
git switch -c feature/<task-name>

# work...
git add .
git commit -m "..."
git push -u origin feature/<task-name>
```
