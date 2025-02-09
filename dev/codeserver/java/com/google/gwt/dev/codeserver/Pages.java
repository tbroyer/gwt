/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.gwt.dev.codeserver;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.dev.json.JsonObject;
import com.google.gwt.thirdparty.guava.common.io.Resources;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Common HTTP responses that send HTML pages. For non-HTML pages, see {@link Responses}.
 */
class Pages {

  /**
   * Returns an HTML page with some JSON code prepended to it.
   * (If the resource file isn't found, returns an ErrorPage instead.)
   *
   * @param variableName  the name of the variable to set on the "window" object.
   * @param json  the data to embed in the script.
   * @param resourceName  the name of the HTML file to send (in the current directory)
   */
  static Response newHtmlPage(final String variableName, final JsonObject json,
      final String resourceName) {

    final URL resource = WebServer.class.getResource(resourceName);
    if (resource == null) {
      return new ErrorPage("resource not found: " + resourceName);
    } else {
      return new HtmlPage(variableName, json, resource);
    }
  }

  /**
   * A response that will send a static HTML page with JSON data prepended.
   */
  static class HtmlPage implements Response {
    final String variableName;
    final JsonObject json;
    final URL resource;

    /**
     * @see {@link #newHtmlPage}.
     */
    private HtmlPage(String variableName, JsonObject json, URL resource) {
      this.variableName = variableName;
      this.json = json;
      this.resource = resource;
    }

    @Override
    public void send(HttpExchange exchange, TreeLogger logger) throws IOException {
      exchange.getResponseHeaders().set("Content-Type", "text/html");
      exchange.sendResponseHeaders(200, 0);

      try (var outBytes = exchange.getResponseBody()) {
        Writer out = new OutputStreamWriter(outBytes, StandardCharsets.UTF_8);

        out.append("<!DOCTYPE html>\n");
        out.append("<script>\n");
        out.append("window." + variableName + " = ");
        json.write(out);
        out.append(";\n");
        out.append("</script>\n");
        out.flush();

        Resources.copy(resource, outBytes);
      }
    }
  }

  /**
   * A response that will send an error page.
   */
  static class ErrorPage implements Response {
    final int status;
    final String message;

    ErrorPage(String message) {
      this(404, message);
    }

    ErrorPage(int httpStatus, String message) {
      this.status = httpStatus;
      this.message = message;
    }

    @Override
    public void send(HttpExchange exchange, TreeLogger logger)
        throws IOException {

      exchange.getResponseHeaders().set("Content-Type", "text/html");
      exchange.sendResponseHeaders(status, 0);

      try (var writer =
          new PrintWriter(exchange.getResponseBody(), false, StandardCharsets.UTF_8)) {
        HtmlWriter out = new HtmlWriter(writer);
        out.startTag("html").nl();

        out.startTag("head").nl();
        out.startTag("title").text("Unavailable (GWT Code Server)").endTag("title").nl();
        out.endTag("head").nl();

        out.startTag("body").nl();
        out.startTag("p").text(message).endTag("p");
        out.endTag("body").nl();

        out.endTag("html").nl();
      }
      logger.log(Type.INFO, "Sent error page: " + message);
    }
  }
}
