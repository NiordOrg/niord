/*
 * Copyright (c) 2023 GLA Research and Development Directorate
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.niord.core.model;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jboss.resteasy.annotations.jaxrs.FormParam;
import org.jboss.resteasy.annotations.providers.multipart.PartType;

import jakarta.ws.rs.core.MediaType;

/**
 * The Mult-Part Body Class.
 * <p/>
 * This class can be used for multi-part upload requests as the request body.
 * It will contain the uploaded file in the "file" field of the header, while
 * the name can be found in the "filename" header field.
 * <p/>
 * This method of uploading can be used as a replacement for the old javax
 * based method used by the Apache Commons fileupload library which is
 * deprecated under the Jakarta framework.
 *
 * @author Nikolaos Vastardis (email: Nikolaos.Vastardis@gla-rad.org)
 */
public class MultipartBody {

    @FormParam("file")
    @Schema(type = SchemaType.STRING, format = "binary", description = "file data")
    public String file;

    @FormParam("filename")
    @PartType(MediaType.TEXT_PLAIN)
    public String filename;

}
