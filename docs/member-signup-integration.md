# 회원 signup 통합 수정 반영

이 커밋은 기존 기능 브랜치에 회원 담당 변경만 반영합니다.

- 최신 develop(c38cacf)에는 아직 로그인 통합 기반이 없습니다.
- 삭제한 MemberSecurityConfig의 역할은 로그인 담당 SecurityConfig가 맡아야 합니다.
- UserService는 로그인 담당의 공용 PasswordEncoder 빈을 주입받습니다.
- 따라서 이 브랜치만으로 애플리케이션 기동 또는 통합 테스트가 완료된 상태는 아닙니다. 로그인 기반 통합이 선행되어야 합니다.
- H2 테스트 설정의 고정 JWT 키는 공개 테스트 fixture이며 실제 운영·개인 비밀키가 아닙니다. 실제 application-secret.properties는 포함하지 않습니다.
- 이전 50개 테스트 통과 기록은 C:/Projects/back-2nd-project-git 통합 작업 폴더 기준입니다. 분리된 기능 브랜치의 단독 통과 기록을 의미하지 않습니다.
- 이 게시 작업에서는 코드 재설계나 전체 테스트 재실행을 하지 않았습니다.
