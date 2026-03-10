-- LHS Adapter implementations need to be registered with Biologics

-- BiomekRowsFirstAdapter
insert into adapter_implementation
  (id,label,description,java_classname,adapter_interface_id)
values(
  adapter_implementation_seq.nextval,
  'Biomek FX (rows-first numbering)',
  'LHS control file in Biomek FX format using rows-first numbering scheme',
  'biologics.sc.adapter.lhs.BiomekRowsFirstAdapter',
  (select id from adapter_interface where java_classname = 'genedata.bx.adapter.LhsControlFileAdapter')
);

-- BiomekRowsFirstWithSortingAdapter
insert into adapter_implementation
  (id,label,description,java_classname,adapter_interface_id)
values(
  adapter_implementation_seq.nextval,
  'Biomek FX (rows-first numbering, sorted)',
  'LHS control file in Biomek FX format using rows-first numbering scheme, sorted by source and destination plate names and well positions.',
  'biologics.sc.adapter.lhs.BiomekRowsFirstWithSortingAdapter',
  (select id from adapter_interface where java_classname = 'genedata.bx.adapter.LhsControlFileAdapter')
);

insert into plate_management_configuration (
	id,
	adapter_implementation_id,
	for_cld,
	for_sc
)
(select
	plate_management_config_seq.nextval,
	ai.id,
	'N', -- CLD
	'Y'  -- SC
	from (
		select id from adapter_implementation
		where java_classname in 
			('biologics.sc.adapter.lhs.BiomekRowsFirstAdapter', 'biologics.sc.adapter.lhs.BiomekRowsFirstWithSortingAdapter')
	) ai
);
