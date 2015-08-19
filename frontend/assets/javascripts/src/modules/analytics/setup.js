/*global guardian:true */
define([
    'src/utils/cookie',
    'src/modules/analytics/ga',
    'src/modules/analytics/ophan',
    'src/modules/analytics/omniture',
    'src/modules/analytics/krux',
    'src/modules/analytics/twitter',
    'src/modules/analytics/facebook',
    'src/modules/analytics/appnexus',
    'src/modules/analytics/crazyegg',
    'src/modules/analytics/optimizely'
], function (
    cookie,
    ga,
    ophan,
    omniture,
    krux,
    twitter,
    facebook,
    appnexus,
    crazyegg,
    optimizely
) {
    'use strict';

    var analyticsEnabled = (
        guardian.analyticsEnabled &&
        !navigator.doNotTrack &&
        !cookie.getCookie('ANALYTICS_OFF_KEY')
    );

    function setupAnalytics() {
        ophan.init();
        omniture.init();
        ga.init();
    }

    function setupThirdParties() {
        krux.init();
        twitter.init();
        facebook.init();
        appnexus.init();
        crazyegg.init();
        optimizely.init();
    }

    function init() {

        if (analyticsEnabled) {
            setupAnalytics();
        }

        if(analyticsEnabled && !guardian.isDev) {
            setupThirdParties();
        }
    }

    return {
        init: init
    };
});