/**
 * @name Extract Call Graph
 * @kind table
 * @id java/call-graph-extraction
 */

import java

from Callable caller, Callable callee, Call call
where
  call.getEnclosingCallable() = caller and
  call.getCallee() = callee and
  // Only include code defined in your source, not compiled dependencies
  callee.fromSource() and
  // Filter out standard libraries using regex
  not callee.getDeclaringType().getPackage().getName().regexpMatch("java\\..*") and
  not callee.getDeclaringType().getPackage().getName().regexpMatch("javax\\..*")
select 
  caller.getQualifiedName() as caller_fqcn,
  callee.getQualifiedName() as callee_fqcn,
  call.getFile().getAbsolutePath() as file_path,
  call.getLocation().getStartLine() as line_number