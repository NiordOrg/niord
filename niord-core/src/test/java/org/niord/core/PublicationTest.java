/*
 * Copyright 2016 Danish Maritime Authority.
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
package org.niord.core;

import org.junit.Test;
import org.niord.core.message.vo.MessagePublicationVo;
import org.niord.core.publication.PublicationUtils;
import org.niord.core.publication.vo.SystemPublicationVo;
import org.niord.model.message.MessageVo;

import static org.junit.Assert.assertNotNull;

/**
 * Publication tests
 */
public class PublicationTest {

    @Test
    public void testPublications() {

        MessageVo msg = new MessageVo();
        msg.checkCreateDesc("da").setPublication("<a publication=\"dk-harbour-pilot\" href=\"http://www.danskehavnelods.dk\" target=\"_blank\">www.danskehavnelods.dk.</a> <a publication=\"dk-firing-areas-2016\" href=\"http://www.soefartsstyrelsen.dk/SikkerhedTilSoes/Sejladsinformation/EfS/ThisYear2/Skydebilag%202016%20DK.pdf\" target=\"_blank\">Oversigt over forsvarets skydepladser 2016, punkt 17, 18.</a>");
        msg.checkCreateDesc("en").setPublication("<a publication=\"dk-harbour-pilot\" href=\"http://www.danskehavnelods.dk\" target=\"_blank\">www.danskehavnelods.dk.</a> <a publication=\"dk-firing-areas-2016\" href=\"http://www.soefartsstyrelsen.dk/SikkerhedTilSoes/Sejladsinformation/EfS/ThisYear2/Skydebilag%202016%20UK.pdf\" target=\"_blank\">Danish List of Firing Practice Areas, section 17, 18.</a>");

        SystemPublicationVo pub = new SystemPublicationVo();
        pub.setPublicationId("dk-harbour-pilot");
        pub.checkCreateDesc("da").setLink("http://www.danskehavnelods.dk");
        pub.checkCreateDesc("da").setMessagePublicationFormat("www.danskehavnelods.dk");
        pub.checkCreateDesc("en").setLink("http://www.danskehavnelods.dk");
        pub.checkCreateDesc("en").setMessagePublicationFormat("www.danskehavnelods.dk");
        MessagePublicationVo msgPub = PublicationUtils.extractMessagePublication(msg, pub, "da");
        assertNotNull(msgPub);
        System.out.println("Msg Pub: " + msgPub);

        pub = new SystemPublicationVo();
        pub.setPublicationId("dk-firing-areas-2016");
        pub.checkCreateDesc("da").setLink("http://www.soefartsstyrelsen.dk/SikkerhedTilSoes/Sejladsinformation/EfS/ThisYear2/Skydebilag%202016%20DK.pdf");
        pub.checkCreateDesc("da").setMessagePublicationFormat("Oversigt over forsvarets skydepladser 2016, punkt ${parameters}");
        pub.checkCreateDesc("en").setLink("http://www.soefartsstyrelsen.dk/SikkerhedTilSoes/Sejladsinformation/EfS/ThisYear2/Skydebilag%202016%20UK.pdf");
        pub.checkCreateDesc("en").setMessagePublicationFormat("Danish List of Firing Practice Areas, section ${parameters}");
        msgPub = PublicationUtils.extractMessagePublication(msg, pub, "da");
        assertNotNull(msgPub);
        System.out.println("Msg Pub: " + msgPub);


        PublicationUtils.updateMessagePublications(msg, pub, "17, 18, 19", null, null);
        msg.getDescs().forEach(d -> System.out.println("MsgPub[" + d.getLang() + "]: " + d.getPublication()));

        pub = new SystemPublicationVo();
        pub.setPublicationId("dk-journal-no");
        pub.checkCreateDesc("da").setMessagePublicationFormat("J.nr. ${parameters}");
        pub.checkCreateDesc("en").setMessagePublicationFormat("J.no ${parameters}");
        PublicationUtils.updateMessagePublications(msg, pub, "235434242", "http://www.google.dk", null);
        msg.getDescs().forEach(d -> System.out.println("MsgPub[" + d.getLang() + "]: " + d.getPublication()));

    }

}
