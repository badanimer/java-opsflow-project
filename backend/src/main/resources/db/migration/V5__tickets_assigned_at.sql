-- OpsFlow database design v1.20: each ticket screen shows the person and the time of its own step.
--
-- The In progress screen shows the person who holds the ticket and the time they got it, whether they
-- took it from the pool or a manager assigned or reassigned it to them; the Resolved screen shows who
-- resolved it and when. The list of each screen is ordered, paged, and filtered by that time.
--
-- assigned_at is the time the current assignee got the ticket. A take, an assignment, and a
-- reassignment write it in the same UPDATE that writes assignee_id, after the locks; a release, by a
-- person or by the system, empties both. A resolution and a cancellation keep it, as they keep the
-- assignee. NULL while the ticket has no assignee.
--
-- The two indexes serve the order of Ticket list order on the two screens: the source first, then the
-- time of the screen. idx_tickets_status_resolved stays for the archive job, which reads the oldest
-- resolved tickets whatever their source.
--
-- The application account already has UPDATE on tickets, so no privilege changes.

ALTER TABLE tickets
    ADD COLUMN assigned_at DATETIME(3) NULL AFTER assignee_id,
    ADD KEY idx_tickets_status_source_assigned (status, source, assigned_at),
    ADD KEY idx_tickets_status_source_resolved (status, source, resolved_at);

-- The tickets that have an assignee now get the time of the history row that gave it to them: the
-- newest row whose to_user_id is the assignee. updated_at is kept, because no person changed the ticket.
UPDATE tickets t
JOIN (
    SELECT h.ticket_id, MAX(h.created_at) AS assigned_at
    FROM ticket_assignment_history h
    JOIN tickets x ON x.id = h.ticket_id AND h.to_user_id = x.assignee_id
    GROUP BY h.ticket_id
) a ON a.ticket_id = t.id
SET t.assigned_at = a.assigned_at, t.updated_at = t.updated_at
WHERE t.assignee_id IS NOT NULL;
