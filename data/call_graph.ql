import java

from Method caller, Method callee, MethodCall call
where
  call.getEnclosingCallable() = caller and
  call.getMethod() = callee and
  // 1. Filter out extremely common utility calls that clutter the graph
  not callee.hasQualifiedName("java.lang", "String", _) and
  not callee.hasQualifiedName("java.util", _, _) and
  not callee.hasQualifiedName("org.apache.commons.lang3", _, _) and
  // 2. Only include internal project calls OR calls to critical frameworks (Spring/Hibernate)
  (
    callee.getQualifiedName().matches("org.vena.%") or 
    callee.getQualifiedName().matches("org.springframework.%") or
    callee.getQualifiedName().matches("org.hibernate.%")
  )
select 
  caller.getQualifiedName() as caller_fqcn,
  callee.getQualifiedName() as callee_fqcn,
  call.getFile().getAbsolutePath() as file_path,
  call.getLocation().getStartLine() as line_number