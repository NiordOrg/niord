/**
 * The Location Tree directive
 * <p>
 * The editor tree can be initialized with "edit-type" set to "simple" or "advanced". If "simple" is chosen, you merely edit
 * a feature geometry. If "advanced" is chosen, you also edit the names of each coordinates.
 * <p>
 * A few things to keep in mind:
 * <ul>
 *     <li>For the exterior and interior linear rings of polygons, the last position is not displayed
 *         since it is the same as the first position.</li>
 * </ul>
 */
angular.module('niord.editor')

    /**
     * Directive that wraps the fancytree jQuery plugin
     */
    .directive('olEditorTree', ['$rootScope', '$compile', '$timeout', 'MapService',
        function ($rootScope, $compile, $timeout, MapService) {
        'use strict';

        return {
            restrict: 'AE',
            scope: {
                feature: '=',
                editType: "@"
            },

            link: function (scope, element) {

                scope.expandedNodeKeys = [];
                scope.activeNodeKey = undefined;
                scope.editType = scope.editType || 'features';

                var tree;
                var container = element.closest(".feature-collection-panel");


                /** Emits a 'gj-editor-update' message to the parent directive **/
                function emit(type, origScope) {
                    scope.$emit('gj-editor-update', {
                        type: type,
                        featureId: scope.feature.getId(),
                        scope: scope.$id,
                        origScope: origScope
                    });
                }


                /** Make the node visible in the parent container **/
                scope.makeVisible = function (node) {
                    if  (node && node.isVisible()) {
                        node.makeVisible();
                        var y = $(node.span).offset().top - container.offset().top;
                        if (y < 0) {
                            container.scrollTop(container.scrollTop() + y);
                        } else if (y > container.height() - 20) {
                            container.scrollTop(container.scrollTop() + (y - container.height() + 20));
                        }
                    }
                };


                /** Adds a new position node (in edit-mode) after the node with the given key **/
                scope.addPosition = function(key) {
                    var node = tree.getNodeByKey(key);
                    if (node) {
                        node.editCreateNode("after", {
                            type: 'Position',
                            coordIndex: node.data.coordIndex,
                            title: "",
                            icon: "/img/marker.png",
                            folder: false,
                            children: []
                        })
                    }
                };


                /** Removes the node with the given key **/
                scope.deleteNode = function(key) {
                    var node = tree.getNodeByKey(key);
                    if (node && node.data.coordIndex !== undefined && node.data.coordCount !== undefined) {
                        node.remove();
                        var geometry = scope.updateFeatureGeometry(tree.rootNode.children[0]);
                        scope.feature.setGeometry(geometry);
                        var fromIndex = node.data.coordIndex;
                        var toIndex = fromIndex + node.data.coordCount;
                        scope.updateCoordNames([
                            { type: 'remove', fromIndex: fromIndex, toIndex: toIndex },
                            { type: 'offset', fromIndex: toIndex, offset: -node.data.coordCount }
                        ]);
                        scope.reloadFeature(true);
                        emit('feature-modified');
                    }
                };


                /** Returns if the position can be deleted **/
                scope.isDeletableNode = function (node) {
                    var parentType = node.parent.data.type;
                    var siblingNo = node.parent.children.length;

                    switch (node.data.type || '') {
                        case 'Position':
                            return (parentType != 'Point') &&
                                ((parentType == 'LineString' && siblingNo > 2) ||
                                (parentType == 'Exterior' && siblingNo > 3) ||
                                (parentType == 'Interior' && siblingNo > 3));
                        case 'Polygon':
                        case 'LineString':
                        case 'MultiPolygon':
                        case 'MultiLineString':
                        case 'Exterior':
                        case 'Interior':
                        case 'GeometryCollection':
                            return siblingNo > 1;
                    }
                    return false;
                };


                /** Activate the node **/
                scope.activateNode = function(event, data) {
                    var node = data.node;
                    scope.makeVisible(node);

                    var html = '';
                    if (node.data.type == 'Position' && node.parent.data.type != 'Point') {
                        html +=
                            "<a href ng-click='addPosition(\"" + node.key + "\")'><i class='glyphicon glyphicon-plus node-btn'></i></a>";
                    }
                    if (scope.isDeletableNode(node)) {
                        html +=
                            "<a href ng-click='deleteNode(\"" + node.key + "\")'><i class='glyphicon glyphicon-minus node-btn'></i></a>";
                    }

                    if (html.length > 0) {
                        html = "<span class='pull-right node-btns'>" + html + "</span>";
                        $(node.span).append($compile( html )( scope ));
                    }
                };


                /** De-activate the node **/
                scope.deactivateNode = function(event, data) {
                    var node = data.node;
                    $(node.span).find(".node-btns").remove();
                };


                /** Remove active node button **/
                scope.removeActiveNodeBtns = function() {
                    // Be brutal
                    $(".node-btns").remove();
                };


                /** Recursively translates the node into a (possibly multi-dimensional) coordinate array **/
                scope.readFeatureCoordinates = function (node) {
                    switch (node.data.type) {
                        case 'Position':
                            return node.data.coordinates;
                        case 'Point':
                            return node.children[0].data.coordinates;
                        default:
                            var coords = [];
                            angular.forEach(node.children, function (childNode) {
                                coords.push(scope.readFeatureCoordinates(childNode));
                            });
                            // For polygons, add the first position as the last position as well
                            if (node.data.type == 'Exterior' || node.data.type == 'Interior') {
                                coords.push(scope.readFeatureCoordinates(node.children[0]));
                            }
                            return coords;
                    }
                };


                /** Recursively translates tree into an OpenLayers geometry **/
                scope.updateFeatureGeometry = function (node) {
                    var geom;
                    switch (node.data.type) {
                        case 'Point':
                            geom = new ol.geom.Point();
                            geom.setCoordinates(scope.readFeatureCoordinates(node));
                            break;
                        case 'LineString':
                            geom = new ol.geom.LineString();
                            geom.setCoordinates(scope.readFeatureCoordinates(node));
                            break;
                        case 'Polygon':
                            geom = new ol.geom.Polygon();
                            geom.setCoordinates(scope.readFeatureCoordinates(node));
                            break;
                        case 'MultiPoint':
                            geom = new ol.geom.MultiPoint();
                            geom.setCoordinates(scope.readFeatureCoordinates(node));
                            break;
                        case 'MultiLineString':
                            geom = new ol.geom.MultiLineString();
                            geom.setCoordinates(scope.readFeatureCoordinates(node));
                            break;
                        case 'MultiPolygon':
                            geom = new ol.geom.MultiPolygon();
                            geom.setCoordinates(scope.readFeatureCoordinates(node));
                            break;
                        case 'GeometryCollection':
                            geom = new ol.geom.GeometryCollection();
                            var childGeoms = [];
                            angular.forEach(node.children, function (childGeom) {
                                childGeoms.push(scope.updateFeatureGeometry(childGeom))
                            });
                            geom.setGeometries(childGeoms);
                            break;
                    }
                    return geom;
                };


                /** Updates the coordinate names **/
                scope.updateCoordNames = function (updates) {
                    var featureNames = FeatureName.readFeatureNames(scope.feature);
                    var changedNames = [];
                    angular.forEach(featureNames, function (name) {
                        if (name.isFeatureCoordName()) {
                            angular.forEach(updates, function (update) {
                                if (update.type == 'offset' && name.getCoordIndex() >= update.fromIndex &&
                                    (update.toIndex === undefined || name.getCoordIndex() < update.toIndex)) {
                                    scope.feature.unset(name.getKey());
                                    name.offset(update.offset);
                                    changedNames.push(name);
                                } else if (update.type == 'remove' && name.getCoordIndex() >= update.fromIndex &&
                                    (update.toIndex === undefined || name.getCoordIndex() < update.toIndex)) {
                                    scope.feature.unset(name.getKey());
                                }
                            });
                        }
                    });
                    angular.forEach(changedNames, function (name) {
                        scope.feature.set(name.getKey(), name.getValue());
                    });
                };


                /** Called before a node is edited **/
                scope.editBeforeEdit = function(event, data) {
                    scope.removeActiveNodeBtns();

                    return data.node.data.type == 'Position' ||
                        data.node.data.type == 'Lang';
                };


                /** Called before a node editor is closed **/
                scope.editBeforeClose = function(event, data) {
                    var node = data.node;
                    if (node.data.type == 'Position') {
                        if (data.isNew && !data.dirty) {
                            return true;
                        }
                        try {
                            parseLatLon(data.input.val());
                            return true;
                        } catch (ex) {
                            data.input.val(data.orgTitle);
                            data.input.focus();
                            data.input.select();
                            return false;
                        }
                    } else {
                        // NB: By default, Fancytree will not allow you to save a blank text.
                        // Force it by setting the save flag to true
                        if (data.input.val() == '' && data.dirty && !data.save) {
                            data.save = true;
                        }
                        // Lang
                        return true;
                    }
                };


                /** Called to save the edited changes **/
                scope.editSave = function(event, data) {
                    var node = data.node;
                    if (node.data.type == 'Position') {
                        try {
                            var pos = parseLatLon(data.input.val());
                            var xy = MapService.fromLonLat([pos.lon, pos.lat]);
                            node.data.coordinates = xy;
                            var geometry = scope.updateFeatureGeometry(tree.rootNode.children[0]);
                            scope.feature.setGeometry(geometry);
                            if (data.isNew) {
                                scope.updateCoordNames([
                                    { type: 'offset', fromIndex: node.data.coordIndex + 1, offset: 1 }
                                ]);
                                scope.reloadFeature(true);
                            }
                            emit('feature-modified');
                            scope.$$phase || scope.$apply();
                            return true;
                        } catch (ex) {
                            console.error("Error " + ex);
                            return false;
                        }
                    } else {
                        // Lang
                        var title = data.input.val();
                        if (title && title.trim().length > 0){
                            scope.feature.set(node.key, title);
                        } else {
                            scope.feature.unset(node.key);
                        }
                        emit('name-updated');
                        return true;
                    }
                };


                /** Initialize the tree **/
                element.fancytree({
                    source: [],
                    keyboard: true,
                    extensions: [ 'edit' ],
                    activate: scope.activateNode,
                    deactivate: scope.deactivateNode,
                    edit: {
                        inputCss: {minWidth: "140px", maxWidth: "140px"},
                        triggerCancel: ["esc", "tab", "click"],
                        triggerStart: ["f2", "dblclick", "shift+click", "mac+enter"],
                        beforeEdit: scope.editBeforeEdit,
                        edit: $.noop,
                        beforeClose: scope.editBeforeClose,
                        save: scope.editSave,
                        close: function(event, data) { scope.makeVisible(data.node); }
                    }
                });

                tree = element.fancytree("getTree");


                /** Stores the currently active and expanded nodes of the tree **/
                scope.storeTreeState = function() {
                    scope.expandedNodeKeys = [];
                    var activeNode = tree.getActiveNode();
                    scope.activeNodeKey = activeNode && activeNode.isVisible() ? activeNode.key : undefined;
                    tree.visit(function(node){
                        if (node.isExpanded() && node.isVisible()) {
                            scope.expandedNodeKeys.push(node.key);
                        }
                    });
                };


                /** Restores the active and expanded nodes of the tree **/
                scope.restoreTreeState = function() {
                    if (scope.expandedNodeKeys) {
                        tree.visit(function(node){
                            node.setExpanded($.inArray(node.key, scope.expandedNodeKeys) > -1);
                        });
                    }
                    if (scope.activeNodeKey) {
                        var activeNode = tree.activateKey(scope.activeNodeKey);
                        if (activeNode && activeNode.isVisible()) {
                            $timeout(function () {
                                scope.makeVisible(activeNode);
                            }, 500);
                        }
                    }
                };


                /** Converts the list of coordinates to tree data **/
                function coordinatesToTreeData(coords, parentType, treeData, key, coordIndex) {
                    var hasLanguageDescs = scope.editType == 'message' &&
                        (parentType == 'Point' || parentType == 'LineString' || parentType == 'Exterior');
                    var isPolygon = parentType == 'Exterior' || parentType == 'Interior';

                    angular.forEach(coords, function (coord, index) {
                        var posLangKey = 'name:' + coordIndex;

                        // For polygons, skip the last coordinate
                        if (isPolygon && index == coords.length - 1) {
                            coordIndex++;
                            return;
                        }

                        var posKey = key + '.' + index;
                        var lonLat = MapService.toLonLat(coord);
                        var posNode = {
                            type: 'Position',
                            key: posKey,
                            title: formatLatLon({ lon: lonLat[0], lat: lonLat[1] }),
                            coordIndex: coordIndex++,
                            coordCount: 1,
                            coordinates: coord,
                            icon: "/img/marker.png",
                            folder: hasLanguageDescs,
                            expanded: false,
                            children: [],
                            lazy: true
                        };
                        treeData.push(posNode);

                        if (hasLanguageDescs) {
                            angular.forEach($rootScope.modelLanguages, function (lang) {
                                var langKey = posLangKey + ':' + lang;
                                var title = scope.feature.get(langKey);
                                posNode.expanded |= title && title.length > 0;
                                var langNode = {
                                    type: 'Lang',
                                    key: langKey,
                                    title: title ? title : "",
                                    icon: "/img/flags/" + lang + ".png",
                                    folder: false
                                };
                                posNode.children.push(langNode);
                            });
                        }
                    });
                    return coordIndex;
                }


                /** Converts an OpenLayers geometry to tree data **/
                function geometryToTreeData(geom, type, treeData, key, coordIndex) {

                    var node = {
                        type: type,
                        key: key,
                        title: type,
                        coordIndex: coordIndex,
                        coordCount: 0,
                        folder: true,
                        children: [],
                        lazy: true,
                        geom: geom
                    };
                    treeData.push(node);


                    switch (type) {
                        case "Point":
                            coordIndex = coordinatesToTreeData([ geom.getCoordinates() ], type, node.children, key, coordIndex);
                            break;
                        case "LineString":
                            coordIndex = coordinatesToTreeData(geom.getCoordinates(), type, node.children, key, coordIndex);
                            break;
                        case "Polygon":
                            if (key.length > 0) {
                                key += ".";
                            }
                            angular.forEach(geom.getLinearRings(), function (ring, index) {
                                var ringType = (index == 0) ? "Exterior" : "Interior";
                                var ringKey = key + index;
                                var ringNode = {
                                    type: ringType,
                                    key: ringKey,
                                    title: ringType,
                                    coordIndex: coordIndex,
                                    coordCount: 0,
                                    folder: true,
                                    children: [],
                                    lazy: true,
                                    geom: ring
                                };
                                node.children.push(ringNode);
                                coordIndex = coordinatesToTreeData(ring.getCoordinates(), ringType, ringNode.children, ringKey, coordIndex);
                                ringNode.coordCount = coordIndex - ringNode.coordCount;
                            });
                            break;
                        case "MultiPoint":
                            angular.forEach(geom.getPoints(), function (point, index) {
                                coordIndex = geometryToTreeData(point, "Point", node.children, key + index, coordIndex);
                            });
                            break;
                        case "MultiLineString":
                            angular.forEach(geom.getLineStrings(), function (lineString, index) {
                                coordIndex = geometryToTreeData(lineString, "LineString", node.children, key + index, coordIndex);
                            });
                            break;
                        case "MultiPolygon":
                            angular.forEach(geom.getPolygons(), function (polygon, index) {
                                coordIndex = geometryToTreeData(polygon, "Polygon", node.children, key + index, coordIndex);
                            });
                            break;
                        case "GeometryCollection":
                            angular.forEach(geom.getGeometries(), function (geometry, index) {
                                coordIndex = geometryToTreeData(geometry, geometry.getType(), node.children, key + index, coordIndex);
                            });
                            break;
                    }
                    node.coordCount = coordIndex - node.coordCount;
                    return coordIndex
                }

                // Will reload the tree data from the feature geometry
                scope.reloadFeature = function (restoreState) {
                    if (restoreState) {
                        scope.storeTreeState();
                    }

                    var treeData = [];
                    geometryToTreeData(scope.feature.getGeometry(), scope.feature.getGeometry().getType(), treeData, "0", 0);
                    tree.options.source = treeData;
                    tree.reload().done(function(){
                        if (restoreState) {
                            scope.restoreTreeState();
                        }
                    });
                };

                scope.reloadFeature(false);


                /***************************/
                /** Event handling        **/
                /***************************/


                /** Listens for a 'gj-editor-update' event **/
                scope.$on('gj-editor-update', function(event, msg) {
                    // Do now process own events and only for the relevant feature
                    if (msg.scope == scope.$id || msg.origScope == scope.$id || msg.featureId != scope.feature.getId()) {
                        return;
                    }

                    switch (msg.type) {
                        case 'feature-added':
                        case 'feature-removed':
                        case 'feature-order-changed':
                        case 'zoom-feature':
                        case 'name-updated':
                            break;

                        case 'feature-modified':
                            scope.reloadFeature(true);
                            break;
                    }
                });


            }

        };
    }]);
