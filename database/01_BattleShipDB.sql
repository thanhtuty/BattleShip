/*
  BattleShip Online - SQL Server schema.
  Execute this file in SQL Server Management Studio (SSMS) with permission to create databases.
*/
IF DB_ID(N'BattleShipDB') IS NULL
BEGIN
    CREATE DATABASE BattleShipDB;
END
GO

USE BattleShipDB;
GO

IF OBJECT_ID(N'dbo.GameUsers', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.GameUsers (
        UserId       INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_GameUsers PRIMARY KEY,
        Username     VARCHAR(20) NOT NULL CONSTRAINT UQ_GameUsers_Username UNIQUE,
        PasswordSalt VARBINARY(16) NOT NULL,
        PasswordHash VARBINARY(32) NOT NULL,
        TotalPoints  INT NOT NULL CONSTRAINT DF_GameUsers_Points DEFAULT (0),
        Wins         INT NOT NULL CONSTRAINT DF_GameUsers_Wins DEFAULT (0),
        Losses       INT NOT NULL CONSTRAINT DF_GameUsers_Losses DEFAULT (0),
        TotalGames   INT NOT NULL CONSTRAINT DF_GameUsers_Games DEFAULT (0),
        CreatedAt    DATETIME2(0) NOT NULL CONSTRAINT DF_GameUsers_Created DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT CK_GameUsers_Stats CHECK (TotalPoints >= 0 AND Wins >= 0 AND Losses >= 0 AND TotalGames >= 0)
    );
END
GO

IF OBJECT_ID(N'dbo.GameMatches', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.GameMatches (
        MatchId          UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_GameMatches PRIMARY KEY,
        Status           VARCHAR(12) NOT NULL,
        CreatedAt        DATETIME2(3) NOT NULL CONSTRAINT DF_GameMatches_Created DEFAULT (SYSUTCDATETIME()),
        StartedAt        DATETIME2(3) NULL,
        FinishedAt       DATETIME2(3) NULL,
        ActiveTurnUserId INT NULL,
        TurnDeadlineAt   DATETIME2(3) NULL,
        WinnerUserId     INT NULL,
        EndReason        VARCHAR(20) NULL,
        CONSTRAINT CK_GameMatches_Status CHECK (Status IN ('SETUP','PLAYING','FINISHED','CANCELLED')),
        CONSTRAINT FK_GameMatches_ActiveTurn FOREIGN KEY (ActiveTurnUserId) REFERENCES dbo.GameUsers(UserId),
        CONSTRAINT FK_GameMatches_Winner FOREIGN KEY (WinnerUserId) REFERENCES dbo.GameUsers(UserId)
    );
END
GO

IF OBJECT_ID(N'dbo.MatchPlayers', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MatchPlayers (
        MatchId    UNIQUEIDENTIFIER NOT NULL,
        UserId     INT NOT NULL,
        PlayerOrder TINYINT NOT NULL,
        IsReady    BIT NOT NULL CONSTRAINT DF_MatchPlayers_Ready DEFAULT (0),
        BoardState VARCHAR(36) NOT NULL CONSTRAINT DF_MatchPlayers_Board DEFAULT (REPLICATE('0',36)),
        ShotsTaken INT NOT NULL CONSTRAINT DF_MatchPlayers_Shots DEFAULT (0),
        CONSTRAINT PK_MatchPlayers PRIMARY KEY (MatchId,UserId),
        CONSTRAINT UQ_MatchPlayers_Order UNIQUE (MatchId,PlayerOrder),
        CONSTRAINT CK_MatchPlayers_Order CHECK (PlayerOrder IN (1,2)),
        CONSTRAINT CK_MatchPlayers_Board CHECK (LEN(BoardState)=36),
        CONSTRAINT CK_MatchPlayers_Shots CHECK (ShotsTaken>=0),
        CONSTRAINT FK_MatchPlayers_Match FOREIGN KEY (MatchId) REFERENCES dbo.GameMatches(MatchId),
        CONSTRAINT FK_MatchPlayers_User FOREIGN KEY (UserId) REFERENCES dbo.GameUsers(UserId)
    );
END
GO

IF OBJECT_ID(N'dbo.GameEvents', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.GameEvents (
        EventId   BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_GameEvents PRIMARY KEY,
        MatchId   UNIQUEIDENTIFIER NOT NULL,
        UserId    INT NULL,
        EventType VARCHAR(24) NOT NULL,
        Details   NVARCHAR(500) NULL,
        CreatedAt DATETIME2(3) NOT NULL CONSTRAINT DF_GameEvents_Created DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT FK_GameEvents_Match FOREIGN KEY (MatchId) REFERENCES dbo.GameMatches(MatchId),
        CONSTRAINT FK_GameEvents_User FOREIGN KEY (UserId) REFERENCES dbo.GameUsers(UserId)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_GameUsers_Ranking' AND object_id=OBJECT_ID(N'dbo.GameUsers'))
    CREATE INDEX IX_GameUsers_Ranking ON dbo.GameUsers(TotalPoints DESC,Wins DESC,Username ASC);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_GameMatches_History' AND object_id=OBJECT_ID(N'dbo.GameMatches'))
    CREATE INDEX IX_GameMatches_History ON dbo.GameMatches(Status,FinishedAt DESC);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_GameEvents_Match_Time' AND object_id=OBJECT_ID(N'dbo.GameEvents'))
    CREATE INDEX IX_GameEvents_Match_Time ON dbo.GameEvents(MatchId,CreatedAt,EventId);
GO

SELECT N'BattleShipDB schema is ready.' AS Result;
