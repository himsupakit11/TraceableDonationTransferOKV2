//'use strict';
//
//define(['viewmodel/Campaign']), function (campaignViewModel){
//    function($http) {
//    return new function () {
//
//        var _this = this;
//        var serverAddr = '';
//
//
//         var endpoint = function endpoint(target) {
//                        return serverAddr + target;
//         };
//
//         this.getCampaign = function (campaignId) {
//         return load('campaign' + campaignId, $http.get(endpoint('/api/campaigns/'+ campaignId))).then(function (resp) {
//               // Do some data modification to simplify the model
//            var campaign = resp.data;
//            return campaign;
//            });
//         };
//
//         this.newCampaign = function() {
//            return campaignViewModel;
//         };
//
//         this.createCampaign = function(campaign) {
//            return load('create-campaign', $http.post(endpoint('api/campaigns'), campaign.toJson())).then(function (resp){
//                return campaign.campaignId;
//            }, function (resp) {
//                    throw resp;
//               });
//         };
//      };
//   };
//};