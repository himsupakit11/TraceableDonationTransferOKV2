'use strict';

function Campaign(campaignViewModel){

    this.toJason = function(){
        var name = {};
        var target = {};
        var deadline = {} ;
        var deadlineTemp = {2019-04-18T10:51:16.532Z };
        var recipientName = {};
        var category = {};
        var description = {};
        var objective = {};

         _.assign(name, campaignViewModel.name);
         _.assign(target, campaignViewModel.target);
         _.assign(deadline, campaignViewModel.deadline);
         _.assign(recipientName, campaignViewModel.recipientName);
         _.assign(category, campaignViewModel.category);
         _.assign(description, campaignViewModel.description);
         _.assign(objective, campaignViewModel.objective);

        //deadlineTemp = formatDateForAngular(deadline);

         var json = {
         name: name,
         target: target,
         deadline: deadlineTemp,
         recipientName: recipientName,
         category: category,
         description: description,
         objective: objective
         };

         return json;
    };
};

//function formatDateForAngular(dateStr) {
//    var parts = dateStr.split("-");
//    return new Date(parts[0], parts[1], parts[2]);
//}
//2019-04-18T10:51:16.532Z