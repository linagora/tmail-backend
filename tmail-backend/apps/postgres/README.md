## Administration Operations
## Clean up data

To clean up some data on the specific TMail data structures, that will be redundant again after a long time, you can execute the SQL queries `clean_up_data_tmail.sql`.

The data that in:
- `label_change` table

Note that the `clean_up_data_tmail.sql` should be merged with [the SQL clean up script on Apache James](https://github.com/apache/james-project/blob/postgresql/server/apps/postgres-app/clean_up.sql) to clean data on James tables as well.
