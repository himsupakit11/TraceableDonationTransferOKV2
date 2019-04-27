//'use strict';
//
//define(['services/NodeApi', 'Campaign','utils/semantic'], function (nodeApi, Campaign) {
//        function CreateCampaignController($http, $scope, $location, nodeService) {
//            semantic.init($scope, nodeService.isLoading);
//
//            $scope.campaign = nodeService.newCampaign();
//            $scope.createCampaign = function () {
//                    nodeService.createCampaign(new Campaign($scope.campaign)).then(function (campaignId){
//                        return $location.path('#/campaign/' + campaignId);
//                    }, function (resp) {
//                            $scope.formError = resp.data;
//                        }, handleHttpFail);
//                };
//                 $('input.percent').mask("9.999999", { placeholder: "", autoclear: false });
//                        $('#swapirscolumns').click(function () {
//                            var first = $('#irscolumns .irscolumn:eq( 0 )');
//                            var last = $('#irscolumns .irscolumn:eq( 1 )');
//                            first.before(last);
//            };
//        };
//    };
//
