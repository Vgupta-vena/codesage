/**
 * @name JAX-RS endpoint mapping
 * @description Finds JAX-RS resource methods and their HTTP method/path mappings.
 * @kind problem
 * @id vgupta/java/jaxrs-endpoint-mapping
 * @problem.severity recommendation
 */

import java

class HttpMethodAnnotation extends Annotation {
  HttpMethodAnnotation() {
    this.getType().hasQualifiedName("javax.ws.rs", "GET") or
    this.getType().hasQualifiedName("javax.ws.rs", "POST") or
    this.getType().hasQualifiedName("javax.ws.rs", "PUT") or
    this.getType().hasQualifiedName("javax.ws.rs", "DELETE") or
    this.getType().hasQualifiedName("javax.ws.rs", "PATCH") or

    this.getType().hasQualifiedName("jakarta.ws.rs", "GET") or
    this.getType().hasQualifiedName("jakarta.ws.rs", "POST") or
    this.getType().hasQualifiedName("jakarta.ws.rs", "PUT") or
    this.getType().hasQualifiedName("jakarta.ws.rs", "DELETE") or
    this.getType().hasQualifiedName("jakarta.ws.rs", "PATCH") or

    this.getType().hasQualifiedName("org.apache.cxf.jaxrs.ext", "PATCH") or
    this.getType().hasQualifiedName("org.vena.rest", "PATCH")
  }

  string getHttpVerb() {
    result = this.getType().getName()
  }
}

predicate hasPathAnnotation(Annotatable a, string path) {
  exists(Annotation an |
    an = a.getAnAnnotation() and
    (
      an.getType().hasQualifiedName("javax.ws.rs", "Path") or
      an.getType().hasQualifiedName("jakarta.ws.rs", "Path")
    ) and
    path = an.getStringValue("value")
  )
}

from Method m, RefType t, HttpMethodAnnotation httpAnn, string classPath, string methodPath
where
  t = m.getDeclaringType() and
  httpAnn = m.getAnAnnotation() and
  (
    hasPathAnnotation(t, classPath) or classPath = ""
  ) and
  (
    hasPathAnnotation(m, methodPath) or methodPath = ""
  )
select
  httpAnn.getHttpVerb(),
  classPath,
  methodPath,
  m.getQualifiedName(),
  m.getFile().getRelativePath()