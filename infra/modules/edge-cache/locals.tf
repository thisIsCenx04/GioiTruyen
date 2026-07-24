locals {
  credential_bypass_expression = join(" ", [
    "(http.host eq \"${var.public_hostname}\") and (",
    "http.request.method not in {\"GET\" \"HEAD\"}",
    "or http.cookie ne \"\"",
    "or len(http.request.headers[\"authorization\"]) gt 0",
    ")"
  ])

  public_catalog_expression = join(" ", [
    "(http.host eq \"${var.public_hostname}\")",
    "and http.request.method in {\"GET\" \"HEAD\"}",
    "and http.cookie eq \"\"",
    "and len(http.request.headers[\"authorization\"]) eq 0",
    "and (",
    "http.request.uri.path eq \"/\"",
    "or http.request.uri.path eq \"/home\"",
    "or http.request.uri.path eq \"/search\"",
    "or starts_with(http.request.uri.path, \"/search/\")",
    "or http.request.uri.path eq \"/stories\"",
    "or starts_with(http.request.uri.path, \"/stories/\")",
    ")"
  ])
}
