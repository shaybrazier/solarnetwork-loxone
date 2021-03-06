SELECT co.uuid_hi, co.uuid_lo, co.config_id, sm.source_id, co.name, co.sort, co.ctype, 
	co.room_hi, co.room_lo, co.cat_hi, co.cat_lo,
	st.name AS state_name, st.event_hi, st.event_lo
FROM  solarnode.loxone_control co
INNER JOIN solarnode.loxone_control_state st1
	ON st1.uuid_hi = co.uuid_hi AND st1.uuid_lo = co.uuid_lo AND st1.config_id = co.config_id
LEFT OUTER JOIN solarnode.loxone_control_state st 
	ON st.uuid_hi = co.uuid_hi AND st.uuid_lo = co.uuid_lo AND st.config_id = co.config_id
LEFT OUTER JOIN solarnode.loxone_smap sm
	ON sm.uuid_hi = co.uuid_hi AND sm.uuid_lo = co.uuid_lo AND sm.config_id = co.config_id
WHERE co.config_id = ? AND st1.event_hi = ? AND st1.event_lo = ?
