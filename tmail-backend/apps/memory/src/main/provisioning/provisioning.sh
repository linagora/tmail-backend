#!/bin/bash

james-cli AddUser alice@localhost aliceSecret
james-cli AddUser bob@localhost bobSecret
james-cli AddUser empty@localhost emptrySecret

declare -a arr=("INBOX" "Important" "tmail" "tmail.mobile" "tmail.backend" "tmail.backend.extensions" "tmail.backend.extensions.pgp" "tmail.backend.extensions.filters" "tmail.backend.extensions.ticketAuth" "tmail.backend.memory" "tmail.backend.distributed" "tmail.marketting" "admin" "customers" "james" "james.dev" "james.user" "james.pmc" "james.dev.gsoc" "Outbox" "Sent" "Drafts" "Trash" "Spam" "loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong" "a.b.c.d.e.f.g.h.i.j")

for i in "${arr[@]}"
do
   echo "Creating mailbox $i"
   james-cli CreateMailbox \#private alice@localhost $i &
   james-cli CreateMailbox \#private bob@localhost $i &
   wait
done

for i in "${arr[@]}"
do
   for j in {1..41}
   do
       echo "Importing $j.eml in $i"
       james-cli ImportEml \#private alice@localhost $i /root/provisioning/eml/$j.eml &
       james-cli ImportEml \#private bob@localhost $i /root/provisioning/eml/$j.eml &
       wait
   done
done

james-cli CreateMailbox \#private alice@localhost empty
james-cli CreateMailbox \#private bob@localhost empty

james-cli CreateMailbox \#private alice@localhost five
james-cli ImportEml \#private alice@localhost five 0.eml
james-cli ImportEml \#private alice@localhost five 1.eml
james-cli ImportEml \#private alice@localhost five 2.eml
james-cli ImportEml \#private alice@localhost five 3.eml
james-cli ImportEml \#private alice@localhost five 4.eml
james-cli CreateMailbox \#private bob@localhost five
james-cli ImportEml \#private bob@localhost five 0.eml
james-cli ImportEml \#private bob@localhost five 1.eml
james-cli ImportEml \#private bob@localhost five 2.eml
james-cli ImportEml \#private bob@localhost five 3.eml
james-cli ImportEml \#private bob@localhost five 4.eml