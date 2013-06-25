/*
 * Copyright (c) 2009, 2010, 2011, 2012, 2013, B3log Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.b3log.solo.processor;


import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.Role;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.servlet.HTTPRequestContext;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.JSONRenderer;
import org.b3log.latke.servlet.renderer.freemarker.AbstractFreeMarkerRenderer;
import org.b3log.latke.util.Locales;
import org.b3log.latke.util.Requests;
import org.b3log.latke.util.Sessions;
import org.b3log.latke.util.Strings;
import org.b3log.solo.SoloServletListener;
import org.b3log.solo.model.Common;
import org.b3log.solo.processor.renderer.ConsoleRenderer;
import org.b3log.solo.processor.util.Filler;
import org.b3log.solo.service.InitService;
import org.b3log.solo.util.QueryResults;
import org.json.JSONObject;


/**
 * B3log Solo initialization service.
 *
 * @author <a href="mailto:DL88250@gmail.com">Liang Ding</a>
 * @version 1.0.0.5, Sep 6, 2012
 * @since 0.4.0
 */
@RequestProcessor
public final class InitProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(InitProcessor.class.getName());

    /**
     * Initialization service.
     */
    @Inject
    private InitService initService;

    /**
     * Filler.
     */
    @Inject
    private Filler filler;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Shows initialization page.
     * 
     * @param context the specified http request context
     * @param request the specified http servlet request
     * @param response the specified http servlet response
     * @throws Exception exception 
     */
    @RequestProcessing(value = "/init", method = HTTPRequestMethod.GET)
    public void showInit(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
        throws Exception {
        if (SoloServletListener.isInited()) {
            response.sendRedirect("/");

            return;
        }

        final AbstractFreeMarkerRenderer renderer = new ConsoleRenderer();

        renderer.setTemplateName("init.ftl");
        context.setRenderer(renderer);

        final Map<String, Object> dataModel = renderer.getDataModel();

        final Map<String, String> langs = langPropsService.getAll(Locales.getLocale(request));

        dataModel.putAll(langs);

        dataModel.put(Common.VERSION, SoloServletListener.VERSION);
        dataModel.put(Common.STATIC_RESOURCE_VERSION, Latkes.getStaticResourceVersion());
        dataModel.put(Common.YEAR, String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));

        Keys.fillServer(dataModel);
        Keys.fillRuntime(dataModel);
        filler.fillMinified(dataModel);
    }

    /**
     * Initializes B3log Solo.
     * 
     * @param context the specified http request context
     * @param request the specified http servlet request, for example,
     * <pre>
     * {
     *     "userName": "",
     *     "userEmail": "",
     *     "userPassword": ""
     * }
     * </pre>
     * @param response the specified http servlet response
     * @throws Exception exception 
     */
    @RequestProcessing(value = "/init", method = HTTPRequestMethod.POST)
    public void initB3logSolo(final HTTPRequestContext context, final HttpServletRequest request,
        final HttpServletResponse response) throws Exception {
        if (SoloServletListener.isInited()) {
            response.sendRedirect("/");

            return;
        }

        final JSONRenderer renderer = new JSONRenderer();

        context.setRenderer(renderer);

        final JSONObject ret = QueryResults.defaultResult();

        renderer.setJSONObject(ret);

        try {
            final JSONObject requestJSONObject = Requests.parseRequestJSONObject(request, response);

            final String userName = requestJSONObject.optString(User.USER_NAME);
            final String userEmail = requestJSONObject.optString(User.USER_EMAIL);
            final String userPassword = requestJSONObject.optString(User.USER_PASSWORD);

            if (Strings.isEmptyOrNull(userName) || Strings.isEmptyOrNull(userEmail) || Strings.isEmptyOrNull(userPassword)
                || !Strings.isEmail(userEmail)) {
                ret.put(Keys.MSG, "Init failed, please check your input");

                return;
            }

            final Locale locale = Locales.getLocale(request);

            requestJSONObject.put(Keys.LOCALE, locale.toString());

            initService.init(requestJSONObject);

            // If initialized, login the admin
            final JSONObject admin = new JSONObject();

            admin.put(User.USER_NAME, requestJSONObject.getString(User.USER_NAME));
            admin.put(User.USER_EMAIL, requestJSONObject.getString(User.USER_EMAIL));
            admin.put(User.USER_ROLE, Role.ADMIN_ROLE);
            admin.put(User.USER_PASSWORD, requestJSONObject.getString(User.USER_PASSWORD));

            Sessions.login(request, response, admin);

            ret.put(Keys.STATUS_CODE, true);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            ret.put(Keys.MSG, e.getMessage());
        }
    }
}
