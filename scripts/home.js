$(function(){
    // 所有.poptip提示
    $('.poptip').popup();

    // sidebar
    $('#sidebar-toggler').on("click", function(){
        $('.sidebar').sidebar({
            overlay: false
        }).sidebar("toggle");
    });

});