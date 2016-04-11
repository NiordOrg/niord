

alter table Feature add spatial index feature_geometry_index (geometry);

alter table AtonNode add spatial index aton_node_geometry (geometry);

