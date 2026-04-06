import java

from Method m
select
  m.getDeclaringType().getPackage().getName(),
  m.getDeclaringType().getQualifiedName(),
  m.getSignature(),
  m.getQualifiedName(),
  m.getLocation().getFile().getRelativePath()