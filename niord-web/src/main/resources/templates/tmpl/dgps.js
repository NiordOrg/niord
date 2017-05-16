
/*
 * Copyright 2017 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

load('niord:templates/tmpl/common.js');

message.areas.clear();
var areaService = CdiUtils.getBean(org.niord.core.area.AreaService.class);
var dk = areaService.findByName('Danmark', 'da', null);
if (dk) {
    var dkVo = dk.toVo(
        org.niord.model.message.AreaVo.class,
        org.niord.model.DataFilter.get());
    message.areas.add(dkVo);
}

var dgpsStation = {
    'radio_navigation.dgps.skagen' :  {
        frequency: 296.0,
        coordinates: Java.to([ 10.59512710571289, 57.74877166748047],"java.lang.Double[]"),
        transmitters: [ 'Baltico', 'Rogaland' ]
    },
    'radio_navigation.dgps.blåvand' :  {
        frequency: 290.0,
        coordinates: Java.to([ 8.08320140838623, 55.5578498840332],"java.lang.Double[]"),
        transmitters: [ 'Baltico', 'Rogaland' ]
    },
    'radio_navigation.dgps.hammer_odde' :  {
        frequency: 289.5,
        coordinates: Java.to([ 14.773825645446777, 55.298221588134766],"java.lang.Double[]"),
        transmitters: [ 'Baltico' ]
    },
    'radio_navigation.dgps.torshavn' :  {
        frequency: 287.5,
        coordinates: Java.to([ -6.837833404541016, 62.02050018310547],"java.lang.Double[]"),
        transmitters: [ 'Rogaland' ]
    }
};

// Update the geometry of the message part
if (params.dgps_station
    && params.dgps_station.key) {
    var station = dgpsStation[params.dgps_station.key];
    if (station !== undefined) {
        part.geometry.features.length = 0;

        var featureObj = {
            'type': 'Feature',
            'properties': {},
            'geometry': {
                'type': 'Point',
                'coordinates': station.coordinates
            }
        };
        var featureJson = org.niord.core.util.JsonUtils.toJson(featureObj);
        var feature = org.niord.core.util.JsonUtils.fromJson(
            featureJson,
            org.niord.model.geojson.FeatureVo.class);
        part.geometry.features = Java.to([ feature ], "org.niord.model.geojson.FeatureVo[]");

        var navtex = message.promulgation('navtex');
        if (navtex !== undefined && navtex.promulgate) {
            for (var t = 0; t < station.transmitters.length; t++) {
                var transmitter = station.transmitters[t];
                if (navtex.transmitters !== undefined && navtex.transmitters.containsKey(transmitter)) {
                    navtex.transmitters.put(transmitter, true);
                }
            }
        }

        params.put('station', station);
    }
}
