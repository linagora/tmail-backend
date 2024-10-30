-- This is a script to delete old rows from some tables. One of the attempts to clean up the never-used data after a long time.

DO
$$
    DECLARE
        days_to_keep INTEGER;
        ticket_ttl_in_seconds INTEGER;
    BEGIN
        -- Set the number of days dynamically
        days_to_keep := 60;

        -- Delete rows older than the specified number of days in the `label_change` table
        DELETE
        FROM label_change
        WHERE created_date < current_timestamp - interval '1 day' * days_to_keep;

        -- Set TTL in seconds for the Ticket table cleanup
        ticket_ttl_in_seconds := 120;

        -- Delete rows older than the specified TTL in the `Ticket` table
        DELETE
        FROM ticket
        WHERE created_date < current_timestamp - interval '1 second' * ticket_ttl_in_seconds;
    END
$$;