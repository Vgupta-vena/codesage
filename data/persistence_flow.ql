/**
 * @name Resource-to-persistence flow
 * @description Finds data flow from resource/controller parameters to persistence-related calls.
 * @kind problem
 * @id vgupta/java/resource-to-persistence-flow
 * @problem.severity warning
 */

import java
import semmle.code.java.dataflow.DataFlow

module FlowConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node source) {
    exists(Method m, Parameter p |
      p = m.getAParameter() and
      source.asParameter() = p and
      (
        m.getDeclaringType().getQualifiedName().matches("%Resource%") or
        m.getDeclaringType().getQualifiedName().matches("%Controller%")
      )
    )
  }

  predicate isSink(DataFlow::Node sink) {
    exists(MethodCall mc |
      sink.asExpr() = mc and
      (
        mc.getMethod().getQualifiedName().matches("%GenericDAO%.save%") or
        mc.getMethod().getQualifiedName().matches("%GenericDAO%.update%") or
        mc.getMethod().getQualifiedName().matches("%GenericDAO%.delete%") or
        mc.getMethod().getQualifiedName().matches("%GenericDAO%.saveOrUpdate%")
      )
    )
  }
}

module Flow = DataFlow::Global<FlowConfig>;

from DataFlow::Node source, DataFlow::Node sink, Parameter p, MethodCall mc, Callable sourceMethod
where
  Flow::flow(source, sink) and
  source.asParameter() = p and
  p.getCallable() = sourceMethod and
  sink.asExpr() = mc
select
  sourceMethod.getQualifiedName(),
  p.getName(),
  mc.getMethod().getQualifiedName(),
  sourceMethod.getDeclaringType().getQualifiedName()