#!/bin/bash
# What osixia/openldap configured on its own, which these stacks were built on: the memberOf and
# refint overlays and the equality indexes. Run once, after the image has loaded schemas/ and ldifs/.
set -euo pipefail
. /opt/bitnami/scripts/libopenldap.sh
ldap_start_bg
ldap_load_module /opt/bitnami/openldap/lib/openldap memberof.so
ldap_load_module /opt/bitnami/openldap/lib/openldap refint.so
ldapmodify -Y EXTERNAL -H "ldapi:///" <<LDIF
dn: olcOverlay=memberof,olcDatabase={2}mdb,cn=config
changetype: add
objectClass: olcOverlayConfig
objectClass: olcMemberOf
olcOverlay: memberof
olcMemberOfDangling: ignore
olcMemberOfRefInt: TRUE
olcMemberOfGroupOC: groupOfUniqueNames
olcMemberOfMemberAD: uniqueMember
olcMemberOfMemberOfAD: memberOf

dn: olcOverlay=refint,olcDatabase={2}mdb,cn=config
changetype: add
objectClass: olcOverlayConfig
objectClass: olcRefintConfig
olcOverlay: refint
olcRefintAttribute: owner
olcRefintAttribute: manager
olcRefintAttribute: uniqueMember
olcRefintAttribute: member
olcRefintAttribute: memberOf

dn: olcDatabase={2}mdb,cn=config
changetype: modify
replace: olcDbIndex
olcDbIndex: uid eq
olcDbIndex: mail eq
olcDbIndex: memberOf eq
olcDbIndex: entryCSN eq
olcDbIndex: entryUUID eq
olcDbIndex: objectClass eq
LDIF
ldap_stop
