/**
 * @name DB and API Touchpoints (Refined)
 * @kind table
 * @id java/touchpoint-extraction-refined
 */

import java

from Method caller, Method callee, MethodCall call, string category
where
  call.getEnclosingCallable() = caller and
  call.getMethod() = callee and

  // Exclude test files
  not caller.getFile().getAbsolutePath().matches("%/src/test/%") and

  // Exclude anonymous / lambda callers
  not caller.getQualifiedName().matches("%<anonymous class>%") and
  not caller.getQualifiedName().matches("%lambda$%") and

  (
    // =========================
    // 1. PERSISTENCE
    // =========================
    (
      callee.hasQualifiedName("javax.persistence", _, _) or
      callee.hasQualifiedName("jakarta.persistence", _, _) or
      callee.getDeclaringType().getQualifiedName().matches("%EntityManager%") or
      callee.getDeclaringType().getQualifiedName().matches("%JdbcTemplate%") or
      callee.getDeclaringType().getQualifiedName().matches("%NamedParameterJdbc%") or
      callee.getDeclaringType().getQualifiedName().matches("%Repository") or
      callee.getDeclaringType().getQualifiedName().matches("%Dao") or
      callee.getDeclaringType().getQualifiedName().matches("%Store") or
      callee.getDeclaringType().getQualifiedName().matches("%GenericDAO%")
    ) and category = "PERSISTENCE"

    or

    // =========================
    // 2. EXTERNAL API
    // =========================
    (
      callee.getDeclaringType().getQualifiedName().matches("%HttpClient%") or
      callee.getDeclaringType().getQualifiedName().matches("%RestTemplate%") or
      callee.getDeclaringType().getQualifiedName().matches("%WebClient%") or
      callee.getDeclaringType().getQualifiedName().matches("%Feign%") or
      callee.getDeclaringType().getQualifiedName().matches("%Auth0Client%") or
      callee.getDeclaringType().getQualifiedName().matches("%Auth0ClientService%") or
      callee.getDeclaringType().getQualifiedName().matches("%ClientService%") or
      callee.getDeclaringType().getQualifiedName().matches("%Integration%") or
      callee.getDeclaringType().getQualifiedName().matches("%Connector%")
    ) and category = "EXTERNAL_API"

    or

    // =========================
    // 3. SCHEDULED TASKS
    // =========================
    exists(Annotation ann |
      ann = caller.getAnAnnotation() and
      ann.getType().hasQualifiedName(
        "org.springframework.scheduling.annotation",
        "Scheduled"
      )
    ) and category = "SCHEDULED_TASK"
  )

select
  category,
  caller.getQualifiedName() as caller_fqcn,
  callee.getQualifiedName() as target_fqcn,
  caller.getFile().getAbsolutePath() as file_path