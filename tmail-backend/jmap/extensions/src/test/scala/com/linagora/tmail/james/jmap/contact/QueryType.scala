package com.linagora.tmail.james.jmap.contact

trait QueryType

case class MatchAllQuery() extends QueryType

case class MatchQuery(field: String, value: String) extends QueryType
