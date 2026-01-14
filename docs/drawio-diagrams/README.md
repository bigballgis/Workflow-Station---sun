# Workflow Platform Architecture Diagrams (Draw.io Format)

## Overview

This folder contains all 10 architecture diagrams in Draw.io format, ready to import into Confluence.

---

## File List

| # | File Name | Description |
|---|-----------|-------------|
| 1 | `01-system-architecture.drawio` | Overall system architecture with all layers |
| 2 | `02-microservices-interaction.drawio` | Microservices communication patterns |
| 3 | `03-workflow-engine-architecture.drawio` | Workflow engine internal components |
| 4 | `04-task-assignment-mechanism.drawio` | Task assignment flow (7 types) |
| 5 | `05-database-architecture.drawio` | Database ER diagram |
| 6 | `06-deployment-architecture.drawio` | Kubernetes deployment topology |
| 7 | `07-technology-stack.drawio` | Frontend and backend technology stack |
| 8 | `08-security-architecture.drawio` | JWT authentication and RBAC authorization |
| 9 | `09-function-unit-design-flow.drawio` | Function unit design process |
| 10 | `10-system-integration.drawio` | External system integration |

---

## How to Import to Confluence

### Method 1: Using Draw.io Plugin (Recommended)

1. **Install Draw.io Plugin**
   - Go to Confluence Administration
   - Navigate to "Find new apps"
   - Search for "draw.io Diagrams for Confluence"
   - Install the plugin

2. **Import Diagram**
   - Create or edit a Confluence page
   - Click "+" button in the toolbar
   - Select "Draw.io Diagram"
   - Choose "Import" option
   - Upload any `.drawio` file from this folder
   - Click "Save"

3. **Edit Diagram**
   - Double-click the diagram to enter edit mode
   - Modify colors, layout, or text as needed
   - Save changes

### Method 2: Using Draw.io Desktop

1. **Download Draw.io Desktop**
   - Visit: https://github.com/jgraph/drawio-desktop/releases
   - Download and install for your OS

2. **Open and Edit**
   - Open any `.drawio` file with Draw.io Desktop
   - Edit the diagram
   - Export as PNG/SVG/PDF
   - Upload to Confluence

### Method 3: Using Draw.io Web

1. **Open Draw.io Web**
   - Visit: https://app.diagrams.net/
   - Click "Open Existing Diagram"
   - Select any `.drawio` file from this folder

2. **Export**
   - File > Export as > PNG/SVG/PDF
   - Upload to Confluence

---

## Diagram Details

### 1. System Architecture
**Components:**
- Frontend Layer (3 applications)
- API Gateway Layer
- Microservices Layer (4 services)
- Workflow Engine (Flowable)
- Data Layer (PostgreSQL, Redis)

**Use Case:** Technical architecture review, system overview

### 2. Microservices Interaction
**Components:**
- User Portal Service
- Workflow Engine Core
- Admin Center Service
- Developer Workstation
- Flowable Engine

**Use Case:** Understanding service dependencies, API design

### 3. Workflow Engine Architecture
**Components:**
- ProcessComponent, TaskProcessComponent
- WorkflowEngineClient
- ProcessController, TaskController
- ProcessEngineComponent, TaskManagerComponent
- TaskAssigneeResolver, TaskAssignmentListener
- Flowable Services

**Use Case:** Workflow engine deep dive, development guide

### 4. Task Assignment Mechanism
**Flow:**
- Process Start → Set Initiator
- Task Created → TaskAssignmentListener
- Read BPMN Properties
- TaskAssigneeResolver
- 7 Assignment Types (Direct vs Candidate)

**Use Case:** Understanding task assignment logic

### 5. Database Architecture
**Tables:**
- admin_* (Admin Center tables)
- dw_* (Developer Workstation tables)
- sys_* (Platform Security tables)
- ACT_* (Flowable tables)

**Use Case:** Database design, data modeling

### 6. Deployment Architecture
**Components:**
- Ingress Layer
- Frontend Pods
- Gateway Pod
- Backend Pods
- Data Layer (StatefulSets)
- Config & Discovery

**Use Case:** DevOps deployment, infrastructure planning

### 7. Technology Stack
**Frontend:**
- Vue 3, Element Plus, BPMN.js, Pinia, Vue Router, Axios, Vue I18n

**Backend:**
- Spring Boot 3.x, Spring Cloud Gateway, Spring Security
- Flowable 7.x, Spring Data JPA, Flyway, Lombok, Jackson

**Data:**
- PostgreSQL 15, Redis 7

**DevOps:**
- Docker, Kubernetes, Helm, Maven, Vite

**Use Case:** Technology selection, team training

### 8. Security Architecture
**Flow:**
- User Login → Validate Credentials → Generate JWT
- API Request → JWT Validation → RBAC Check → Business Logic

**Use Case:** Security review, authentication design

### 9. Function Unit Design Flow
**Steps:**
1. Create Function Unit
2. Design Table Structure
3. Define Fields
4. Set Foreign Keys
5. Create Forms
6. Bind Form-Table Relations
7. Configure Form Rules (form-create)
8. Create Actions
9. Configure Actions (config_json)
10. Design Process (BPMN)
11. Bind Nodes (Forms + Actions)
12. Configure Assignees (7 types)
13. Deploy Process
14. Test Process

**Use Case:** Developer guide, feature development

### 10. System Integration
**External Systems:**
- LDAP/AD (User Directory)
- Email Service (SMTP)
- SMS Service (SMS Gateway)
- File Storage (MinIO/S3)

**Monitoring:**
- Prometheus (Monitoring)
- Grafana (Visualization)
- ELK Stack (Log Analysis)

**Use Case:** Integration planning, monitoring setup

---

## Color Scheme

| Color | Usage | Hex Code |
|-------|-------|----------|
| Light Blue | Frontend Layer | #E1F5FF |
| Orange | API Gateway | #FFF4E6 |
| Green | Microservices | #E8F5E9 |
| Pink | Workflow Engine | #FCE4EC |
| Purple | Data Layer | #F3E5F5 |
| Yellow | Notes/Highlights | #FFF9C4 |
| Gray | Config/Infrastructure | #F5F5F5 |

---

## Editing Tips

### Changing Colors
1. Select the shape
2. Click "Fill Color" in toolbar
3. Choose new color

### Adding Components
1. Click "+" button in left panel
2. Drag shapes to canvas
3. Connect with arrows

### Exporting
- **PNG**: File > Export as > PNG (for presentations)
- **SVG**: File > Export as > SVG (for web, scalable)
- **PDF**: File > Export as > PDF (for printing)

---

## Version Control

To track changes in Git:

1. **Export as XML**
   - Draw.io files are already XML format
   - Git can track changes

2. **Use Descriptive Commit Messages**
   ```bash
   git add docs/drawio-diagrams/
   git commit -m "Update system architecture diagram - add Redis cache"
   ```

3. **Review Changes**
   ```bash
   git diff docs/drawio-diagrams/01-system-architecture.drawio
   ```

---

## Troubleshooting

### Issue: Cannot open .drawio file in Confluence
**Solution:** Ensure Draw.io plugin is installed and enabled

### Issue: Diagram looks different in Confluence
**Solution:** Fonts may differ. Use standard fonts like Arial or Helvetica

### Issue: Chinese characters display incorrectly
**Solution:** All diagrams use English only. No Chinese characters.

### Issue: Diagram is too large
**Solution:** 
- In Draw.io: File > Page Setup > Adjust size
- Or export as image and resize

---

## Support

For questions or issues:
1. Check Confluence Draw.io plugin documentation
2. Visit Draw.io help: https://www.diagrams.net/doc/
3. Contact architecture team

---

**Version:** 1.0  
**Last Updated:** 2026-01-14  
**Language:** English  
**Format:** Draw.io (.drawio)  
**Total Diagrams:** 10
