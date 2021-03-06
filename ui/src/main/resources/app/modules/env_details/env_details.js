define([
  "genesis",
  "services/backend",
  "utils/poller",
  "modules/status",
  "modules/env_details/history",
  "modules/env_details/access",
  "variables",
  "modules/common/templates",
  "modules/common/env_status",
  "use!backbone",
  "jquery",
  "use!jqueryui",
  "use!jvalidate"
],

function (genesis, backend, poller, status, EnvHistory, EnvAccess, variables, gtemplates, EnvStatus, Backbone, $) {
  var EnvironmentDetails = genesis.module();

  EnvironmentDetails.Model = Backbone.Model.extend({
    defaults: {
      "id" : 0,
      "name": "",
      "creator": "",
      "manifest": {},
      "status": "",
      "completed": 0,
      "templateName": "",
      "templateVersion": "",
      "projectId": "",
      "historyCount": 0,
      "finishedActionsCount": 0
    },

    urlRoot: function () {
      return "/rest/projects/" + this.get("projectId") + "/envs";
    }
  });

  EnvironmentDetails.Views.Details = Backbone.View.extend({
    template: "app/templates/env_details.html",

    vmsTemplate: "app/templates/env_details/vm_table.html",
    staticServersTemplate: "app/templates/env_details/servers_table.html",
    envAttributesTemplate: "app/templates/common/attribute_table.html",

    details: null,
    historyCollection: null,
    workflowHistory: null,

    confirmationDialog: null,
    executeWorkflowDialog: null,
    resetEnvStatusDialog: null,

    events: {
      "click #tab3": "renderWorkflowList",
      "click .action-button:not(.disabled)": "executeWorkflow",
      "click .cancel-button:not(.disabled)": "cancelWorkflow",
      "click .reset-button:not(.disabled)": "resetEnvStatus",
      "click a.show-sources" : "showSources",
      "click a.envname": "showEditName",
      "click a#update-name": "updateName",
      "click a#cancel-name": "hideEditName"
    },

    initialize: function (options) {
      this.details = new EnvironmentDetails.Model({"id": options.envId, projectId: options.projectId});

      poller.PollingManager.start(this.details);

      this.details.bind("change:status", this.updateControlButtons, this);
      this.details.bind("change:vms", this.renderVirtualMachines, this);
      this.details.bind("change:servers", this.renderServers, this);
      this.details.bind("change:servers change:vms", this.checkServersAndVms, this);
      this.details.bind("change:attributes change:modificationTime", this.renderAttributes, this);

      this.executeWorkflowDialog = new ExecuteWorkflowDialog().
        bind('workflow-started', function(workflow) {
          status.StatusPanel.information("Workflow '" + workflow.name + "' execution started");
        }).
        bind('workflow-starting-error', function(workflow, errors) {
          status.StatusPanel.error(errors);
        });

      this.historyCollection = new EnvHistory.Collection([], {"projectId": options.projectId, "envId": options.envId});
    },

    onClose: function () {
      poller.PollingManager.stop(this.details);
      if(this.executeWorkflowDialog) {
        this.executeWorkflowDialog.destroy();
        this.executeWorkflowDialog = null;
      }
      if(this.confirmationDialog) {
        this.confirmationDialog.dialog('destroy').remove();
      }
      if(this.resetEnvStatusDialog) {
        this.resetEnvStatusDialog.dialog('destroy').remove();
      }
      genesis.utils.nullSafeClose(this.workflowHistory);
      genesis.utils.nullSafeClose(this.sourcesView);
      genesis.utils.nullSafeClose(this.accessView);
    },

    showSources: function(event) {
      var $currentTarget = $(event.currentTarget),
          templateName = $currentTarget.attr("data-template-name"),
          templateVersion = $currentTarget.attr("data-template-version");

      if(!this.sourcesView) {
        this.sourcesView = new gtemplates.SourcesView({
          el: $("<pre class='prettyprint linenums' id='template-source-view'></pre>"),
          projectId: this.details.get("projectId")
        });
      }

      this.sourcesView.showTemplate(templateName, templateVersion);
    },

    cancelWorkflow: function () {
      this.confirmationDialog.dialog('open');
    },

    resetEnvStatus: function () {
      this.resetEnvStatusDialog.dialog('open');
    },

    executeWorkflow: function (e) {
      var workflowName = e.currentTarget.rel;

      genesis.app.trigger("page-view-loading-started");

      var template = new gtemplates.TemplateModel(
        {
          name: this.details.get('templateName'),
          version: this.details.get('templateVersion')
        },
        {
          projectId: this.details.get("projectId")
        }
      );

      var self = this;

      $.when(template.fetch())
        .done(function() {
          var workflow = _(template.get('workflows')).find(function (item) {
            return item.name === workflowName;
          });

          if (workflow) {
            var wmodel = new gtemplates.WorkflowModel({
                name: self.details.get('templateName'),
                version: self.details.get('templateVersion')
              },
              {
                projectId: self.details.get("projectId"),
                workflow: workflowName
              });

            $.when(wmodel.fetch()).done(function(){
              self.executeWorkflowDialog.showFor(self.details.get("projectId"), wmodel.get("result"), self.details.get("id"),
              self.details.get('templateName'), self.details.get('templateVersion'));
            }).fail(function(jqXHR){
              genesis.app.trigger("page-view-loading-completed");
              status.StatusPanel.error(jqXHR);
            });
          }
        }).fail(function(jqXHR) {
          status.StatusPanel.error(jqXHR);
          genesis.app.trigger("page-view-loading-completed");
        })
    },

    updateControlButtons: function () {
      var status = this.details.get('status'),
          activeExecution = (status === "Busy");

      this.$(".cancel-button")
        .toggleClass("disabled", !activeExecution)
        .toggle(status !== "Destroyed");

      this.$(".action-button")
        .toggleClass("disabled", activeExecution)
        .toggle(status !== "Destroyed");

      this.$("#resetBtn")
        .toggle(status === "Broken");
    },

    showEditName: function() {
      $('h1 > a.envname').hide();
      $('#nameedit').show();
    },

    hideEditName: function() {
      $('#nameedit').hide();
      $('h1 > a.envname').show();
    },

    updateName: function() {
      var view = this;
      genesis.app.trigger("page-view-loading-started");
      var name = $("#new-name").val();
      $.when(backend.EnvironmentManager.updateEnvName(view.details.get("projectId"), view.details.get('id'), name)).done(function() {
        $('a.envname').html(name);
        view.hideEditName();
      }).fail(function(jqxhr) {
        status.StatusPanel.error(jqxhr);
      }).always(function() {
        genesis.app.trigger("page-view-loading-completed");
      });
    },

    renderVirtualMachines: function() {
      var view = this;
      var vms = view.details.get("vms");
      $.when(genesis.fetchTemplate(this.vmsTemplate)).done(function(tmpl) {
        view.$("#vm-list").html(tmpl({
          vms : _.filter(vms, function(vm) { return vm.status !== "Destroyed"; })
        }));
      });
    },

    renderServers: function() {
      var servers = this.details.get("servers"),
          view = this;

      $.when(genesis.fetchTemplate(this.staticServersTemplate)).done(function(tmpl) {
        view.$("#servers-list").html(tmpl({
          servers : _.filter(servers, function(server) { return server.status !== "Released"; })
        }));
      });
    },

    checkServersAndVms: function() {
      if(_.all(this.details.get("vms"), function(vm) { return vm.status === "Destroyed" }) &&
        _.all(this.details.get("servers"), function(server) { return server.status === "Released"; })) {
        this.$("#no-servers-message").show();
      } else {
        this.$("#no-servers-message").hide();
      }
    },

    renderAttributes: function() {
      var view = this;
      $.when(genesis.fetchTemplate(this.envAttributesTemplate)).done(function(tmpl) {
        view.$("#panel-tab-1").html(tmpl({
          attributes: _.sortBy(view.details.get("attributes"), function(attr) { return attr.description; }),
          environment: view.details.toJSON(),
          utils: genesis.utils
        }));
      });
    },

    renderWorkflowList: function () {
      if (this.workflowHistory == null) {
        this.workflowHistory = new EnvHistory.View({model: this.details, collection: this.historyCollection, el: "#panel-tab-3"});
        this.workflowHistory.render();
      }
    },

    renderAccess: function() {
      var access = new EnvAccess.Model({}, { projectId: this.details.get("projectId"), envId: this.details.id });
      this.accessView = new EnvAccess.View({
        el: "#panel-tab-4",
        accessConfiguration: access,
        projectId: this.details.get("projectId"),
        tabHeader: this.$("#permissions-tab-header")
      });
    },

    _renderAllSubViews: function() {
      var statusView = new EnvStatus.View({el: this.$(".env-status"), model: this.details});
      statusView.render();

      this.renderVirtualMachines();
      this.renderServers();
      this.checkServersAndVms();
      this.renderWorkflowList();
      this.renderAttributes();
      this.renderAccess();
    },

    render: function () {
      var view = this;
      $.when(
        genesis.fetchTemplate(this.template),
        genesis.fetchTemplate(this.staticServersTemplate),  //prefetching template
        genesis.fetchTemplate(this.vmsTemplate),            //prefetching template
        this.details.fetch()
      ).done(function (tmpl) {
          var details = view.details.toJSON();

          view.$el.html(tmpl({
            environment: details,
            actions: _(details.workflows).reject(function (flow) {
              return flow.name === details.createWorkflowName
            })
          }));

          view.updateControlButtons();
          view._renderAllSubViews();

          view.confirmationDialog = view.createConfirmationDialog(view.$("#dialog-confirm"));
          view.resetEnvStatusDialog = view.createResetEnvStatusDialog(view.$("#dialog-reset"));
      }).fail(function() {
          genesis.app.trigger("server-communication-error",
            "Failed to get environment details<br/><br/> Please contact administrator.",
            "/project/" + view.details.get("projectId")
          );
      }).always(function(){
          genesis.app.trigger("page-view-loading-completed");
      });
    },

    createConfirmationDialog: function (element) {
      var self = this;
      return element.dialog({
        title: 'Confirmation',
        buttons: {
          "Yes": function () {

            $.when(backend.WorkflowManager.cancelWorkflow(self.details.get("projectId"), self.details.get('id')))
              .done(function() {
                status.StatusPanel.information("'Cancel workflow' signal sent");
              })
              .fail(function() {
                status.StatusPanel.error("Failed to sent 'Cancel workflow' signal");
              });
            $(this).dialog("close");
          },

          "No": function () {
            $(this).dialog("close");
          }
        }
      });
    },

    createResetEnvStatusDialog: function (element) {
      var self = this;
      return element.dialog({
        title: 'Confirmation',
        buttons: {
          "Yes": function () {
            $.when(backend.EnvironmentManager.resetEnvStatus(self.details.get("projectId"), self.details.get('id')))
              .done(function() {
                status.StatusPanel.information("Environment status was changed to 'Ready'");
              })
              .fail(function(jqXHR) {
                status.StatusPanel.error(jqXHR);
              });
            $(this).dialog("close");
          },
          "No": function () {
            $(this).dialog("close");
          }
        }
      });
    }
  });

  var ExecuteWorkflowDialog = Backbone.View.extend({
    template: "app/templates/environment_variables.html",

    initialize: function() {
      this.$el.id = "#workflowParametersDialog";
    },

    showFor: function(projectId, workflow, envId, templateName, templateVersion) {
      this.workflow = workflow;
      this.envId = envId;
      this.projectId = projectId;
      this.templateName = templateName;
      this.templateVersion = templateVersion;
      this.render();
    },

    destroy: function() {
      this.unbind();
      this.$el.dialog('destroy');
      this.remove();
    },

    runWorkflow: function() {
      var vals = {};
      if (this.workflow.variables.length > 0) {
        if (!$('#workflow-parameters-form').valid()) {
          this.trigger("workflow-validation-errors");
          return;
        }

        $('.workflow-variable').each(function () {
            if ($(this).val()) vals[$(this).attr('name')] = $(this).is("input[type='checkbox']") ? $(this).is(':checked') : $(this).val();
        });
      }
      var execution = backend.WorkflowManager.executeWorkflow(this.projectId, this.envId, this.workflow.name, vals);

      var view = this;
      $.when(execution).then(
        function success() {
          view.trigger("workflow-started", view.workflow);
          view.$el.dialog("close");
        },
        function fail(response) {
          var json = {};
          try {
            json = JSON.parse(response.responseText);
          } catch (e) {
            if(response.statusText && response.statusText  === "timeout") {
              json = {compoundVariablesErrors: [], compoundServiceErrors: ["Timeout: server taking too long time to respond"] }
            } else {
              json = {compoundVariablesErrors: [], compoundServiceErrors: ["Internal server error"] }
            }
          }
          if (_.isEmpty(json.variablesErrors)) {
            var errors = _.union(
              json.compoundVariablesErrors,
              json.compoundServiceErrors,
              _.values(json.serviceErrors)
            );
            view.trigger("workflow-starting-error", view.workflow, errors);
            view.$el.dialog("close");
          } else {
            view.trigger("workflow-validation-errors");
            var validator = $('#workflow-parameters-form').validate();
            validator.showErrors(json.variablesErrors);
          }
        }
      );

    },

    render: function() {
      var view = this;
      $.when(genesis.fetchTemplate(this.template)).done(function (tmpl) {
        variables.processVars({
          variables: view.workflow.variables,
          projectId: view.projectId,
          workflowName: view.workflow.name,
          templateName: view.templateName,
          templateVersion: view.templateVersion
        });

        genesis.app.trigger("page-view-loading-completed");
        view.$el.html(tmpl({variables: view.workflow.variables, workflowName: view.workflow.name}));

        view.$el.dialog({
          title: 'Execute ' + view.workflow.name,
          width: _.size(view.workflow.variables) > 0 ? 600 : 400,
          autoOpen: true,
          buttons: {
            "Run": function(e) {
              var $thisButton = $(this).parent().find("button:contains('Run')"),
                  disabled = $thisButton.button( "option", "disabled" );
              if(!disabled) {
                $thisButton.button("disable");

                view.unbind("workflow-validation-errors");
                view.bind("workflow-validation-errors", function() {
                  $thisButton.button("enable");
                });

                view.runWorkflow();
              }
            },

            "Cancel": function () {
              $(this).dialog( "close" );
            }
          }
        });
      });
    }
  });

  return EnvironmentDetails;
});
