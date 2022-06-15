af.expose(this,
/* Full expose path     */ "af.bim.extensions.view.AccessPoints",
/* Dependencies         */ ["af.components.Options"],
/* Dependencies aliases */ function(Options) {
"use strict";

//#region[ Constants ]#################################################################
//#endregion

return class AccessPoints {
    //#region[ Variables ]#############################################################
    #coreViewer;
    #view;
    #boundOnClick;
    #enabled = false;
    //#endregion

    constructor(coreViewer, options) {
        options = new Options({
            defaults: {
            },
            required: ['view'],
            passed: options 
        });
        //#region Verify valid options
        //#endregion
        this.#view = coreViewer.getViewByName(options.view);
        this.#coreViewer = coreViewer;
    }

    //#region[ Methods ]###############################################################
    //#region(   Public Static  )------------------------------------------------------
    //#endregion
    //#region(   Private Static )------------------------------------------------------
    //#endregion
    //#region(   Public         )------------------------------------------------------
    init(){
        this.#coreViewer.eventHandler.attach("onClick", this.#onClick.bind(this));
    }
    enable(){
        this.#enabled = true;
    }
    disable(){
        this.#enabled = false;
    }
    //#region(     Getters      )------------------------------------------------------
    get onClickCallback(){
        return this.#boundOnClick
    };
    //#endregion
    //#region(     Setters      )------------------------------------------------------
    set onClickCallback(onClickCallback){    
        return this.#boundOnClick = onClickCallback.bind(this);
    };
    //#endregion
    //#endregion
    //#region(   Private        )------------------------------------------------------
    #onClick(e){
        if(this.#enabled && e[this.#view.idx]){
            this.#view.raycaster.setFromCamera(e[this.#view.idx].coords, this.#view.camera);
            var intersections = this.#view.raycaster.intersectBimObjects(this.#view);
            if (intersections.length) {
                this.onClickCallback(intersections[0].point);
            }
            return false;
        }
    };
    //#region(     Callbacks    )------------------------------------------------------
    //#endregion
    //#endregion
    //#endregion
}});