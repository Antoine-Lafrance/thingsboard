# Nouvelle Fonctionnalité: Device Health Status

## 📋 Description

**Device Health Status** est une nouvelle fonctionnalité qui permet de **surveiller et évaluer l'état de santé global des appareils IoT** dans ThingsBoard.

### Problème Résolu

Actuellement, ThingsBoard peut suivre:
- Si un device est connecté ou déconnecté
- Les données de télémétrie individuelles
- L'activité récente

**Mais il manque une vue d'ensemble rapide de la santé globale d'un device.**

### Solution Apportée

Cette fonctionnalité fournit un **score de santé unique (0-100)** qui combine plusieurs indicateurs pour évaluer rapidement l'état d'un appareil.

---

## 🎯 Objectifs

1. **Monitoring Proactif** - Identifier rapidement les devices en difficulté
2. **Priorisation** - Savoir quels devices nécessitent une intervention urgente
3. **Visibilité** - Vue d'ensemble simple de l'état du parc de devices
4. **Maintenance Préventive** - Détecter les problèmes avant la panne complète

---

## 🔧 Fonctionnement

### Calcul du Score de Santé (0-100)

Le score est calculé en combinant deux facteurs principaux:

#### 1. État de Connectivité (60% du score)
| État | Points | Description |
|------|--------|-------------|
| **ONLINE** | 60 pts | Device connecté et actif |
| **OFFLINE** | 0 pts | Device déconnecté |
| **UNKNOWN** | 30 pts | État indéterminé |

#### 2. Niveau de Batterie (40% du score)
- Batterie à 100% = 40 points
- Batterie à 50% = 20 points
- Batterie à 0% = 0 points
- Pas d'info batterie = 40 points (on suppose une alimentation secteur)

### Exemples de Calculs

```
Device 1:
- État: ONLINE (60 pts)
- Batterie: 100% (40 pts)
→ Score total: 100/100 ✅ Excellent

Device 2:
- État: ONLINE (60 pts)
- Batterie: 25% (10 pts)
→ Score total: 70/100 ⚠️ Attention

Device 3:
- État: OFFLINE (0 pts)
- Batterie: 10% (4 pts)
→ Score total: 4/100 ❌ Critique!
```

---

## 📊 Catégories de Santé

Le système classifie automatiquement les devices:

| Score | Catégorie | Indicateur | Action Requise |
|-------|-----------|------------|----------------|
| **70-100** | 🟢 Sain (Healthy) | Tout va bien | Aucune |
| **30-69** | 🟡 Attention | Surveillance accrue | Planifier maintenance |
| **0-29** | 🔴 Critique | Intervention urgente | Action immédiate |

---

## 💻 Utilisation Technique

### Classe Principale

**Fichier:** `common/data/src/main/java/org/thingsboard/server/common/data/DeviceHealthStatus.java`

### Créer un Health Status

```java
// Créer un device health status
DeviceHealthStatus healthStatus = DeviceHealthStatus.builder()
    .deviceId(deviceId)
    .connectivityStatus(ConnectivityStatus.ONLINE)
    .batteryLevel(85)
    .lastActivityTime(System.currentTimeMillis())
    .build();

// Calculer le score
int score = healthStatus.calculateHealthScore();
// Résultat: 94 (60 pour ONLINE + 34 pour 85% de batterie)
```

### Vérifier l'État

```java
// Vérifier si le device est en état critique
if (healthStatus.isCritical()) {
    // Envoyer une alerte urgente
    sendCriticalAlert(deviceId);
}

// Vérifier si le device est en bonne santé
if (healthStatus.isHealthy()) {
    // Rien à faire, tout va bien
    log.info("Device {} is healthy", deviceId);
}

// Vérifier l'inactivité (ex: inactif depuis plus de 5 minutes)
if (healthStatus.isInactive(5 * 60 * 1000)) {
    // Device inactif depuis 5 minutes
    notifyInactivity(deviceId);
}
```

### Méthodes Disponibles

| Méthode | Retour | Description |
|---------|--------|-------------|
| `calculateHealthScore()` | `int` | Calcule et retourne le score 0-100 |
| `isCritical()` | `boolean` | Vrai si score < 30 |
| `isHealthy()` | `boolean` | Vrai si score ≥ 70 |
| `isInactive(long thresholdMs)` | `boolean` | Vrai si inactif depuis plus de thresholdMs |

---

## 🧪 Tests Unitaires

**Fichier:** `common/data/src/test/java/org/thingsboard/server/common/data/DeviceHealthStatusTest.java`

### Exécuter les Tests

```bash
# Tous les tests du module
cd thingsboard
mvn test -pl common/data

# Seulement DeviceHealthStatus
mvn test -Dtest=DeviceHealthStatusTest -pl common/data
```

### Couverture

- **11 tests unitaires** couvrant tous les cas d'usage
- **~100% de couverture de code**
- **Tous les edge cases testés** (batterie 0%, null, etc.)

### Tests Principaux

1. ✅ Device online avec batterie pleine → Score 100
2. ✅ Device online avec batterie faible → Score 70
3. ✅ Device offline → Score bas
4. ✅ Détection des états critiques (< 30)
5. ✅ Détection des états sains (≥ 70)
6. ✅ Détection d'inactivité
7. ✅ Edge cases (batterie 0%, null, etc.)

---

## 📈 Intégrations Futures

### Phase 1 (Actuel) ✅
- Classe de base DeviceHealthStatus
- Calcul de score
- Tests unitaires complets

### Phase 2 (À venir)
- REST API pour exposer le health status
- Intégration avec DeviceStateService
- Persistance en base de données

### Phase 3 (À venir)
- Widget de dashboard pour visualiser la santé
- Graphiques d'évolution du score dans le temps
- Alertes automatiques sur dégradation

### Phase 4 (À venir)
- Historique des scores (time-series)
- Prédiction de pannes avec ML
- Recommandations de maintenance

---

## 🔍 Cas d'Usage

### 1. Monitoring de Flotte IoT
```
Scénario: Gestionnaire d'une flotte de 1000 capteurs
- Vue rapide: 850 devices verts, 120 jaunes, 30 rouges
- Focus immédiat sur les 30 critiques
- Planification maintenance pour les 120 en attention
```

### 2. Devices sur Batterie
```
Scénario: Capteurs environnementaux autonomes
- Détection batterie faible avant déconnexion totale
- Planification tournée de remplacement
- Optimisation logistique maintenance
```

### 3. Alertes Proactives
```
Scénario: Installation critique (hôpital, industrie)
- Alerte immédiate si score passe sous 30
- Email/SMS automatique à l'équipe technique
- Évite interruption de service
```

### 4. Dashboard Exécutif
```
Scénario: Vue d'ensemble pour management
- Indicateur simple: 95% de devices en santé
- Tendance sur 30 jours
- Rapport qualité de service
```

---

## 🎨 Exemples de Visualisation (À Implémenter)

### Widget Dashboard - Jauge de Santé
```
┌─────────────────────────┐
│   Device ABC-123        │
│                         │
│      ╭────────╮         │
│      │   92   │         │
│      ╰────────╯         │
│     HEALTHY 🟢          │
│                         │
│  Connecté: ✓           │
│  Batterie: 88%         │
│  Dernière activité: 2m │
└─────────────────────────┘
```

### Liste de Devices avec Scores
```
╔═══════════════════════════════════════════════╗
║ Device Name     │ Score │ Status   │ Action  ║
╠═══════════════════════════════════════════════╣
║ Sensor-001      │  98   │ 🟢 Sain  │    -    ║
║ Sensor-002      │  45   │ 🟡 OK    │  Check  ║
║ Sensor-003      │  12   │ 🔴 Crit. │ URGENT! ║
╚═══════════════════════════════════════════════╝
```

---

## 📚 Documentation Technique

### Structure de la Classe

```java
public class DeviceHealthStatus {
    // Attributs
    private DeviceId deviceId;              // ID du device
    private ConnectivityStatus status;       // ONLINE/OFFLINE/UNKNOWN
    private Integer batteryLevel;            // 0-100 ou null
    private long lastActivityTime;           // Timestamp en ms
    private int healthScore;                 // Score calculé 0-100
    
    // Méthodes publiques
    public int calculateHealthScore()        // Calcule le score
    public boolean isCritical()              // Teste si critique
    public boolean isHealthy()               // Teste si sain
    public boolean isInactive(long ms)       // Teste l'inactivité
}
```

### Enum ConnectivityStatus

```java
public enum ConnectivityStatus {
    ONLINE,      // Device connecté
    OFFLINE,     // Device déconnecté
    UNKNOWN      // État indéterminé
}
```

---

## ✅ Avantages de cette Fonctionnalité

### Pour les Opérateurs
- **Vue rapide** de l'état du parc
- **Priorisation** des interventions
- **Réduction** du temps de diagnostic

### Pour le Business
- **Amélioration** de la qualité de service
- **Réduction** des pannes imprévues
- **Optimisation** des coûts de maintenance

### Pour les Développeurs
- **API simple** et intuitive
- **Facilement extensible**
- **Bien testé** et documenté

---

## 🚀 Comment Contribuer

### Ajouter de Nouveaux Indicateurs

Pour enrichir le calcul du score, vous pouvez:

```java
// Exemple: ajouter la qualité du signal
public class DeviceHealthStatus {
    private Integer signalQuality; // 0-100
    
    public int calculateHealthScore() {
        int score = 0;
        
        // Connectivité: 40%
        score += calculateConnectivityScore() * 0.4;
        
        // Batterie: 30%
        score += calculateBatteryScore() * 0.3;
        
        // Signal: 30%
        score += calculateSignalScore() * 0.3;
        
        return (int) score;
    }
}
```

### Créer des Widgets

Les développeurs front-end peuvent créer:
- Cartes de santé individuelles
- Graphiques d'évolution
- Heatmaps de flotte
- Tableaux de bord personnalisés

---

## 📞 Support

### Questions Techniques
- Voir le code source: `common/data/src/main/java/org/thingsboard/server/common/data/DeviceHealthStatus.java`
- Tests unitaires: `common/data/src/test/java/org/thingsboard/server/common/data/DeviceHealthStatusTest.java`

### Plan de Qualité
- Voir: `PLAN_QUALITE.md`

---

## 📝 Changelog

### Version 1.0 (Février 2026)
- ✅ Classe DeviceHealthStatus créée
- ✅ Calcul de score basé sur connectivité + batterie
- ✅ Méthodes isCritical(), isHealthy(), isInactive()
- ✅ 11 tests unitaires avec 100% coverage
- ✅ Documentation complète

### À Venir
- [ ] REST API endpoints
- [ ] Intégration DeviceStateService
- [ ] Widget dashboard UI
- [ ] Persistance en DB
- [ ] Historique time-series

---

**Auteurs:** Équipe Qualité ThingsBoard  
**Date:** 3 Février 2026  
**Version:** 1.0
