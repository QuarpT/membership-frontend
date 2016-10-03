define([
    'modules/checkout/formElements',
    'modules/checkout/ratePlanChoice',
], function (formElements, ratePlanChoice) {
    'use strict';

    return function() {
        let packageName = ratePlanChoice.getSelectedRatePlanName();
        let deliveredProduct = formElements.$DELIVERED_PRODUCT_TYPE.val();
        let isPaper = 'paper' === deliveredProduct;

        let validDays = {
            voucher: {
                sixday: (date) => date.day() === 1,
                sunday: (date) => date.day() === 6,
                weekend: (date) => date.day() === 6,
                allDays: (date) => date.day() === 1
            },
            paper: {
                sixday: (date) => date.day() !== 0,
                sunday: (date) => date.day() === 0,
                weekend: (date) => date.day() === 6 || date.day() === 0,
                allDays: (date) => true
            }
        };

        let filters = isPaper ? validDays.paper : validDays.voucher;

        switch (true) {
            case /Sunday/i.test(packageName):
                return filters.sunday;
            case /Sixday/i.test(packageName):
                return filters.sixday;
            case /Weekend/i.test(packageName):
                return filters.weekend;
            default:
                return filters.allDays;
        }
    };
});