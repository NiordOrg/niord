

alter table TestShape add spatial index shape_index (geometry);

alter table AtonNode add spatial index aton_node_geometry (geometry);

